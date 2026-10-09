using Microsoft.Win32;
using Windows.Devices.Bluetooth;
using Windows.Devices.Bluetooth.Rfcomm;
using Windows.Devices.Enumeration;
using Windows.Networking.Sockets;
using Windows.Storage.Streams;

namespace GlidrCompanion;

/// <summary>
/// Keeps a Bluetooth serial (RFCOMM) connection to the Glidr app on the paired phone and sends it lines of JSON.
/// The phone is the server; this side only connects to a phone that is already paired with this PC.
/// </summary>
sealed class PhoneLink : IDisposable
{
    // Must match CompanionLink.SERVICE_UUID in the Android app.
    static readonly Guid ServiceUuid = Guid.Parse("7b2f3c10-6a1e-4c8e-9d52-2f6b1a9c4e01");
    const string RegKey = @"Software\GlidrCompanion";

    readonly CancellationTokenSource _cts = new();
    readonly SemaphoreSlim _sendGate = new(1, 1);
    StreamSocket? _socket;
    DataWriter? _writer;

    public event Action<string>? StatusChanged;
    /// <summary>Raised on every new connection so the current track (with cover art) can be re-sent.</summary>
    public event Action? Connected;

    public bool IsConnected => _writer != null;

    public void Start() => _ = Task.Run(RunAsync);

    async Task RunAsync()
    {
        while (!_cts.IsCancellationRequested)
        {
            try
            {
                StatusChanged?.Invoke("Looking for your phone…");
                var service = await FindPhoneAsync();
                if (service == null)
                {
                    await Task.Delay(TimeSpan.FromSeconds(6), _cts.Token);
                    continue;
                }

                var socket = new StreamSocket();
                await socket.ConnectAsync(
                    service.ConnectionHostName, service.ConnectionServiceName,
                    SocketProtectionLevel.BluetoothEncryptionWithAuthentication);
                _socket = socket;
                _writer = new DataWriter(socket.OutputStream) { UnicodeEncoding = UnicodeEncoding.Utf8 };
                var name = service.Device.Name;
                SaveRemembered(service.Device.BluetoothAddress);
                Log.Write($"connected to {name}");
                StatusChanged?.Invoke($"Connected to {name}");
                Connected?.Invoke();

                // Reading detects when the phone goes away; the app never sends anything back yet.
                using var reader = new DataReader(socket.InputStream);
                while (!_cts.IsCancellationRequested)
                {
                    var n = await reader.LoadAsync(1);
                    if (n == 0) break;
                    reader.ReadByte();
                }
            }
            catch (OperationCanceledException)
            {
                break;
            }
            catch (Exception e)
            {
                Log.Write($"link error: {e.Message}");
            }
            finally
            {
                Drop();
            }

            try
            {
                await Task.Delay(TimeSpan.FromSeconds(4), _cts.Token);
            }
            catch (OperationCanceledException)
            {
                break;
            }
        }
    }

    void Drop()
    {
        try { _writer?.DetachStream(); } catch { }
        try { _writer?.Dispose(); } catch { }
        try { _socket?.Dispose(); } catch { }
        _writer = null;
        _socket = null;
    }

    /// <summary>Tries the phone used last time first, then any paired device that currently has a link to this PC.</summary>
    async Task<RfcommDeviceService?> FindPhoneAsync()
    {
        var tried = new HashSet<ulong>();
        var remembered = LoadRemembered();
        if (remembered != 0)
        {
            tried.Add(remembered);
            var s = await TryDeviceAsync(await BluetoothDevice.FromBluetoothAddressAsync(remembered));
            if (s != null) return s;
        }

        var infos = await DeviceInformation.FindAllAsync(BluetoothDevice.GetDeviceSelectorFromPairingState(true));
        foreach (var info in infos)
        {
            if (_cts.IsCancellationRequested) return null;
            var device = await BluetoothDevice.FromIdAsync(info.Id);
            if (device == null || !tried.Add(device.BluetoothAddress)) continue;
            if (device.ConnectionStatus != BluetoothConnectionStatus.Connected) continue;
            var s = await TryDeviceAsync(device);
            if (s != null) return s;
        }
        return null;
    }

    static async Task<RfcommDeviceService?> TryDeviceAsync(BluetoothDevice? device)
    {
        if (device == null) return null;
        try
        {
            var result = await device.GetRfcommServicesForIdAsync(RfcommServiceId.FromUuid(ServiceUuid), BluetoothCacheMode.Uncached);
            return result.Error == BluetoothError.Success && result.Services.Count > 0 ? result.Services[0] : null;
        }
        catch (Exception e)
        {
            Log.Write($"probe {device.Name}: {e.Message}");
            return null;
        }
    }

    static ulong LoadRemembered()
    {
        using var k = Registry.CurrentUser.OpenSubKey(RegKey);
        return k?.GetValue("Phone") is string s && ulong.TryParse(s, out var v) ? v : 0;
    }

    static void SaveRemembered(ulong address)
    {
        using var k = Registry.CurrentUser.CreateSubKey(RegKey);
        k.SetValue("Phone", address.ToString());
    }

    /// <summary>Sends one JSON line. Drops the message if the phone is not connected.</summary>
    public async Task SendLineAsync(string json)
    {
        var writer = _writer;
        if (writer == null) return;
        await _sendGate.WaitAsync();
        try
        {
            writer.WriteString(json);
            writer.WriteByte((byte)'\n');
            await writer.StoreAsync();
        }
        catch (Exception e)
        {
            Log.Write($"send failed: {e.Message}");
            Drop();
        }
        finally
        {
            _sendGate.Release();
        }
    }

    public void Dispose()
    {
        _cts.Cancel();
        Drop();
    }
}
