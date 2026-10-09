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

/// <summary>Tray icon with a status line, "Start with Windows" and Quit. There is no window.</summary>
sealed class TrayContext : ApplicationContext
{
    const string RunKey = @"Software\Microsoft\Windows\CurrentVersion\Run";
    const string RunName = "GlidrCompanion";

    readonly NotifyIcon _tray;
    readonly ToolStripMenuItem _status;
    readonly ToolStripMenuItem _startup;
    readonly PhoneLink _link = new();
    readonly MediaWatcher _watcher;

    public TrayContext()
    {
        _status = new ToolStripMenuItem("Starting…") { Enabled = false };
        _startup = new ToolStripMenuItem("Start with Windows") { CheckOnClick = true, Checked = IsStartupEnabled() };
        _startup.CheckedChanged += (_, _) => SetStartup(_startup.Checked);
        var openLog = new ToolStripMenuItem("Open log");
        openLog.Click += (_, _) => OpenLog();
        var quit = new ToolStripMenuItem("Quit Glidr Companion");
        quit.Click += (_, _) => ExitThread();

        var menu = new ContextMenuStrip();
        menu.Items.AddRange(new ToolStripItem[] { _status, new ToolStripSeparator(), _startup, openLog, quit });

        _tray = new NotifyIcon { Icon = LoadIcon(), Text = "Glidr Companion", ContextMenuStrip = menu, Visible = true };

        _link.StatusChanged += s => SetStatus(s);
        _watcher = new MediaWatcher(_link);
        _link.Start();
        _ = StartWatcherAsync();
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
        _tray.Visible = false;
        _tray.Dispose();
        _watcher.Dispose();
        _link.Dispose();
        base.ExitThreadCore();
    }
}
