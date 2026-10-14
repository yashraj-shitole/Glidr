using System.Text.Json;
using Windows.Foundation.Collections;
using Windows.Graphics.Imaging;
using Windows.Media.Control;
using Windows.Storage.Streams;

namespace GlidrCompanion;

/// <summary>
/// Watches the Windows "now playing" session (the same one the volume flyout and lock screen show) and
/// sends changes to the phone as JSON lines.
/// </summary>
sealed class MediaWatcher : IDisposable
{
    readonly PhoneLink _link;
    readonly CancellationTokenSource _cts = new();
    readonly SemaphoreSlim _publishGate = new(1, 1);
    GlobalSystemMediaTransportControlsSessionManager? _manager;
    GlobalSystemMediaTransportControlsSession? _session;
    string _lastKey = "";
    string? _lastArt;
    int _pending;
    // A newly connected phone needs the cover art again. Kept as a flag so the request survives being coalesced
    // into a publish that was already pending.
    int _forceArt;

    public MediaWatcher(PhoneLink link)
    {
        _link = link;
        _link.Connected += () => _ = PublishAsync(forceArt: true);
    }

    public async Task StartAsync()
    {
        _manager = await GlobalSystemMediaTransportControlsSessionManager.RequestAsync();
        _manager.CurrentSessionChanged += (_, _) => Rebind();
        Rebind();
        _ = Task.Run(HeartbeatAsync);
    }

    void Rebind()
    {
        if (_session != null)
        {
            _session.MediaPropertiesChanged -= OnChanged;
            _session.PlaybackInfoChanged -= OnChanged;
            _session.TimelinePropertiesChanged -= OnChanged;
        }
        _session = _manager?.GetCurrentSession();
        if (_session != null)
        {
            _session.MediaPropertiesChanged += OnChanged;
            _session.PlaybackInfoChanged += OnChanged;
            _session.TimelinePropertiesChanged += OnChanged;
        }
        Log.Write(_session == null ? "no media session" : $"media session: {_session.SourceAppUserModelId}");
        _ = PublishAsync(forceArt: false);
    }

    void OnChanged(object? sender, object e) => _ = PublishAsync(forceArt: false);

    /// <summary>Resends the position every few seconds so the phone's progress bar cannot drift.</summary>
    async Task HeartbeatAsync()
    {
        while (!_cts.IsCancellationRequested)
        {
            try { await Task.Delay(TimeSpan.FromSeconds(5), _cts.Token); } catch { break; }
            if (_link.IsConnected) await PublishAsync(forceArt: false);
        }
    }

    async Task PublishAsync(bool forceArt)
    {
        if (forceArt) Interlocked.Exchange(ref _forceArt, 1);
        // Coalesce bursts of change events into one message.
        if (Interlocked.Exchange(ref _pending, 1) == 1) return;
        await Task.Delay(120);
        Interlocked.Exchange(ref _pending, 0);

        await _publishGate.WaitAsync();
        try
        {
            if (!_link.IsConnected) return;
            forceArt = Interlocked.Exchange(ref _forceArt, 0) == 1;
            var session = _session;
            if (session == null)
            {
                _lastKey = "";
                _lastArt = null;
                await _link.SendLineAsync("{\"t\":\"none\"}");
                return;
            }

            var props = await session.TryGetMediaPropertiesAsync();
            var playback = session.GetPlaybackInfo();
            var timeline = session.GetTimelineProperties();
            var playing = playback.PlaybackStatus == GlobalSystemMediaTransportControlsSessionPlaybackStatus.Playing;

            var title = props.Title ?? "";
            var artist = props.Artist ?? "";
            var album = props.AlbumTitle ?? "";
            if (title.Length == 0 && artist.Length == 0)
            {
                await _link.SendLineAsync("{\"t\":\"none\"}");
                return;
            }

            var positionMs = (long)timeline.Position.TotalMilliseconds;
            if (playing && timeline.LastUpdatedTime != default)
            {
                positionMs += (long)Math.Max(0, (DateTimeOffset.Now - timeline.LastUpdatedTime).TotalMilliseconds);
            }
            var durationMs = (long)(timeline.EndTime - timeline.StartTime).TotalMilliseconds;
            if (durationMs > 0) positionMs = Math.Min(positionMs, durationMs);

            var key = $"{title}|{artist}|{album}";
            string? art = null;
            if (forceArt || key != _lastKey)
            {
                _lastArt = await ReadArtAsync(props, title, artist);
                _lastKey = key;
                art = _lastArt;
            }

            var msg = new Dictionary<string, object?>
            {
                ["t"] = "np",
                ["title"] = title,
                ["artist"] = artist,
                ["album"] = album,
                ["app"] = FriendlyApp(session.SourceAppUserModelId),
                ["playing"] = playing,
                ["pos"] = Math.Max(0, positionMs),
                ["dur"] = Math.Max(0, durationMs),
            };
            if (art != null) msg["art"] = art;
            await _link.SendLineAsync(JsonSerializer.Serialize(msg));
        }
        catch (Exception e)
        {
            Log.Write($"publish failed: {e.Message}");
        }
        finally
        {
            _publishGate.Release();
        }
    }

    /// <summary>
    /// Cover art as base64 JPEG at up to 1280px on the long side (never scaled up), high quality. Windows gives
    /// browsers only ~120px, so for YouTube the real thumbnail is fetched instead (see <see cref="HighResArt"/>).
    /// </summary>
    async Task<string?> ReadArtAsync(GlobalSystemMediaTransportControlsSessionMediaProperties props, string title, string artist)
    {
        try
        {
            string? small = null;
            var smallPx = 0u;
            if (props.Thumbnail != null)
            {
                using var source = await props.Thumbnail.OpenReadAsync();
                var decoder = await BitmapDecoder.CreateAsync(source);
                smallPx = Math.Max(decoder.PixelWidth, decoder.PixelHeight);
                Log.Write($"cover art source {decoder.PixelWidth}x{decoder.PixelHeight}");
                small = await EncodeAsync(decoder);
            }
            if (smallPx >= 480) return small;

            var hq = await HighResArt.FindAsync(title, artist);
            if (hq != null)
            {
                using var mem = new InMemoryRandomAccessStream();
                using (var w = new DataWriter(mem.GetOutputStreamAt(0)))
                {
                    w.WriteBytes(hq);
                    await w.StoreAsync();
                    w.DetachStream();
                }
                mem.Seek(0);
                var decoder = await BitmapDecoder.CreateAsync(mem);
                return await EncodeAsync(decoder);
            }
            return small;
        }
        catch (Exception e)
        {
            Log.Write($"cover art failed: {e.Message}");
            return null;
        }
    }

    static async Task<string> EncodeAsync(BitmapDecoder decoder)
    {
        var scale = Math.Min(1.0, 1280.0 / Math.Max(decoder.PixelWidth, decoder.PixelHeight));
        var transform = new BitmapTransform
        {
            ScaledWidth = Math.Max(1u, (uint)(decoder.PixelWidth * scale)),
            ScaledHeight = Math.Max(1u, (uint)(decoder.PixelHeight * scale)),
            InterpolationMode = BitmapInterpolationMode.Fant,
        };
        var pixels = await decoder.GetPixelDataAsync(
            BitmapPixelFormat.Bgra8, BitmapAlphaMode.Ignore, transform,
            ExifOrientationMode.RespectExifOrientation, ColorManagementMode.DoNotColorManage);

        using var output = new InMemoryRandomAccessStream();
        var quality = new BitmapPropertySet
        {
            { "ImageQuality", new BitmapTypedValue(0.95f, Windows.Foundation.PropertyType.Single) },
        };
        var encoder = await BitmapEncoder.CreateAsync(BitmapEncoder.JpegEncoderId, output, quality);
        encoder.SetPixelData(BitmapPixelFormat.Bgra8, BitmapAlphaMode.Ignore, transform.ScaledWidth, transform.ScaledHeight, 96, 96, pixels.DetachPixelData());
        await encoder.FlushAsync();

        var bytes = new byte[output.Size];
        using var reader = new DataReader(output.GetInputStreamAt(0));
        await reader.LoadAsync((uint)output.Size);
        reader.ReadBytes(bytes);
        Log.Write($"cover art sent {transform.ScaledWidth}x{transform.ScaledHeight}, {bytes.Length / 1024} KB");
        return Convert.ToBase64String(bytes);
    }

    /// <summary>"Spotify.exe" or "Microsoft.ZuneMusic_8wekyb3d8bbwe!Microsoft.ZuneMusic" to a readable name.</summary>
    static string FriendlyApp(string aumid)
    {
        if (string.IsNullOrWhiteSpace(aumid)) return "";
        var name = aumid.Contains('!') ? aumid[(aumid.LastIndexOf('!') + 1)..] : aumid;
        name = name.EndsWith(".exe", StringComparison.OrdinalIgnoreCase) ? name[..^4] : name;
        var dot = name.LastIndexOf('.');
        if (dot >= 0 && dot < name.Length - 1) name = name[(dot + 1)..];
        return name.Length > 0 ? char.ToUpperInvariant(name[0]) + name[1..] : "";
    }

    public void Dispose() => _cts.Cancel();
}
