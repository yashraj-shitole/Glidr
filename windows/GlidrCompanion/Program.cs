using Microsoft.Win32;

namespace GlidrCompanion;

static class Program
{
    [STAThread]
    static void Main()
    {
        using var single = new Mutex(true, @"Local\GlidrCompanion.SingleInstance", out var first);
        if (!first) return;

        ApplicationConfiguration.Initialize();
        Application.Run(new TrayContext());
    }
}

/// <summary>Tray icon with a status line, "Start with Windows", update check and Quit. There is no window.</summary>
sealed class TrayContext : ApplicationContext
{
    const string RunKey = @"Software\Microsoft\Windows\CurrentVersion\Run";
    const string RunName = "GlidrCompanion";

    readonly NotifyIcon _tray;
    readonly ToolStripMenuItem _status;
    readonly ToolStripMenuItem _startup;
    readonly ToolStripMenuItem _update;
    readonly ToolStripMenuItem _checkUpdate;
    readonly PhoneLink _link = new();
    readonly MediaWatcher _watcher;
    readonly SynchronizationContext _ui;
    readonly System.Windows.Forms.Timer _updateTimer = new() { Interval = (int)TimeSpan.FromHours(24).TotalMilliseconds };
    GlidrRelease? _latest;
    string? _notified;

    public TrayContext()
    {
        _status = new ToolStripMenuItem("Starting…") { Enabled = false };
        _startup = new ToolStripMenuItem("Start with Windows") { CheckOnClick = true, Checked = IsStartupEnabled() };
        _startup.CheckedChanged += (_, _) => SetStartup(_startup.Checked);
        var hq = new ToolStripMenuItem("High-quality cover art (looks up thumbnails online)") { CheckOnClick = true, Checked = HighResArt.Enabled };
        hq.CheckedChanged += (_, _) => HighResArt.Enabled = hq.Checked;
        _update = new ToolStripMenuItem("") { Visible = false };
        _update.Click += (_, _) => OpenUrl(_latest?.ExeUrl ?? _latest?.Page);
        _checkUpdate = new ToolStripMenuItem($"Check for updates (version {Updates.Current})");
        _checkUpdate.Click += (_, _) => _ = CheckUpdatesAsync(manual: true);
        var openLog = new ToolStripMenuItem("Open log");
        openLog.Click += (_, _) => OpenLog();
        var quit = new ToolStripMenuItem("Quit Glidr Companion");
        quit.Click += (_, _) => ExitThread();

        var menu = new ContextMenuStrip();
        menu.Items.AddRange(new ToolStripItem[] { _status, _update, new ToolStripSeparator(), _startup, hq, _checkUpdate, openLog, quit });
        // Creating the menu installed the WinForms context on this (UI) thread. Link and watcher events arrive on
        // background threads and are posted here before they touch any control.
        _ui = SynchronizationContext.Current ?? new WindowsFormsSynchronizationContext();

        _tray = new NotifyIcon { Icon = LoadIcon(), Text = "Glidr Companion", ContextMenuStrip = menu, Visible = true };
        _tray.BalloonTipClicked += (_, _) => OpenUrl(_latest?.ExeUrl ?? _latest?.Page);

        _link.StatusChanged += s => _ui.Post(_ => SetStatus(s), null);
        // A newly connected phone hears about the latest release too, so it can update even without internet.
        _link.Connected += () => { var r = _latest; if (r != null) _ = _link.SendLineAsync(Updates.PhoneMessage(r)); };
        _watcher = new MediaWatcher(_link);
        _link.Start();
        _ = StartWatcherAsync();

        _updateTimer.Tick += (_, _) => _ = CheckUpdatesAsync(manual: false);
        _updateTimer.Start();
        _ = Task.Delay(TimeSpan.FromSeconds(15)).ContinueWith(_ => _ui.Post(_ => _ = CheckUpdatesAsync(manual: false), null));
    }

    /// <summary>Runs on the UI thread (the awaits resume there).</summary>
    async Task CheckUpdatesAsync(bool manual)
    {
        _checkUpdate.Enabled = false;
        _checkUpdate.Text = "Checking for updates…";
        try
        {
            var r = await Updates.LatestAsync();
            if (r == null)
            {
                if (manual) _tray.ShowBalloonTip(4000, "Glidr Companion", "No release has been published yet.", ToolTipIcon.Info);
                return;
            }
            _latest = r;
            if (_link.IsConnected) _ = _link.SendLineAsync(Updates.PhoneMessage(r));
            var newer = Updates.Compare(r.Version, Updates.Current) > 0;
            _update.Visible = newer;
            _update.Text = $"Download update {r.Version}";
            if (newer && (manual || _notified != r.Version))
            {
                _notified = r.Version;
                _tray.ShowBalloonTip(8000, "Glidr Companion update", $"Version {r.Version} is available. Click to download it.", ToolTipIcon.Info);
            }
            else if (!newer && manual)
            {
                _tray.ShowBalloonTip(4000, "Glidr Companion", $"You have the latest version ({Updates.Current}).", ToolTipIcon.Info);
            }
        }
        catch (Exception e)
        {
            Log.Write($"update check failed: {e.Message}");
            if (manual) _tray.ShowBalloonTip(4000, "Glidr Companion", "Could not check for updates. Are you online?", ToolTipIcon.Warning);
        }
        finally
        {
            _checkUpdate.Enabled = true;
            _checkUpdate.Text = $"Check for updates (version {Updates.Current})";
        }
    }

    static void OpenUrl(string? url)
    {
        if (url == null || !url.StartsWith("https://github.com/", StringComparison.Ordinal)) return;
        System.Diagnostics.Process.Start(new System.Diagnostics.ProcessStartInfo(url) { UseShellExecute = true });
    }

    async Task StartWatcherAsync()
    {
        try
        {
            await _watcher.StartAsync();
        }
        catch (Exception e)
        {
            Log.Write($"media watcher failed: {e}");
            SetStatus("Can't read Windows media status");
        }
    }

    void SetStatus(string text)
    {
        // NotifyIcon text is limited to 63 characters.
        var tip = ("Glidr · " + text);
        _tray.Text = tip.Length > 63 ? tip[..63] : tip;
        _status.Text = text;
    }

    static Icon LoadIcon()
    {
        using var s = typeof(TrayContext).Assembly.GetManifestResourceStream("glidr.png");
        if (s == null) return SystemIcons.Application;
        using var bmp = new Bitmap(s);
        var h = bmp.GetHicon();
        return Icon.FromHandle(h);
    }

    static bool IsStartupEnabled()
    {
        using var k = Registry.CurrentUser.OpenSubKey(RunKey);
        return k?.GetValue(RunName) != null;
    }

    static void SetStartup(bool on)
    {
        using var k = Registry.CurrentUser.CreateSubKey(RunKey);
        if (on) k.SetValue(RunName, $"\"{Environment.ProcessPath}\"");
        else k.DeleteValue(RunName, throwOnMissingValue: false);
    }

    static void OpenLog()
    {
        var path = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "GlidrCompanion", "log.txt");
        if (File.Exists(path)) System.Diagnostics.Process.Start(new System.Diagnostics.ProcessStartInfo(path) { UseShellExecute = true });
    }

    protected override void ExitThreadCore()
    {
        _updateTimer.Dispose();
        _tray.Visible = false;
        _tray.Dispose();
        _watcher.Dispose();
        _link.Dispose();
        base.ExitThreadCore();
    }
}
