namespace GlidrCompanion;

/// <summary>Tiny rolling log in %LOCALAPPDATA%\GlidrCompanion\log.txt for troubleshooting.</summary>
static class Log
{
    static readonly object Gate = new();
    static readonly string Path = System.IO.Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "GlidrCompanion", "log.txt");

    public static void Write(string message)
    {
        try
        {
            lock (Gate)
            {
                Directory.CreateDirectory(System.IO.Path.GetDirectoryName(Path)!);
                var info = new FileInfo(Path);
                if (info.Exists && info.Length > 256 * 1024) File.Delete(Path);
                File.AppendAllText(Path, $"{DateTime.Now:HH:mm:ss.fff} {message}{Environment.NewLine}");
            }
        }
        catch
        {
            // Logging must never break the app.
        }
    }
}
