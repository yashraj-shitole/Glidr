using System.Diagnostics;
using System.Text.Json;
using System.Text.RegularExpressions;
using System.Windows.Automation;
using Microsoft.Win32;

namespace GlidrCompanion;

/// <summary>
/// Windows only hands out a 120-256px thumbnail for browser media. To get the real artwork this looks the
/// track up by name:
///   1. the active browser tab's address (exact video id),
///   2. a YouTube search for the title and channel, accepted only when both match,
///   3. Apple's music catalogue (artist + title) for songs, at up to 1400x1400.
/// Only the track title and artist leave the PC, and only while "High-quality cover art" is ticked in the tray menu.
/// </summary>
static partial class HighResArt
{
    const string RegKey = @"Software\GlidrCompanion";
    static readonly string[] Browsers = { "chrome", "msedge", "brave", "vivaldi", "opera", "firefox" };
    static readonly HttpClient Http = CreateClient();
    static readonly Dictionary<string, byte[]?> Cache = new();

    [GeneratedRegex(@"(?:[?&]v=|youtu\.be/|/shorts/|/embed/)([A-Za-z0-9_-]{11})")]
    private static partial Regex VideoId();

    [GeneratedRegex(@"var ytInitialData = (\{.*?\});</script>", RegexOptions.Singleline)]
    private static partial Regex InitialData();

    static HttpClient CreateClient()
    {
        var c = new HttpClient { Timeout = TimeSpan.FromSeconds(10) };
        c.DefaultRequestHeaders.UserAgent.ParseAdd("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36");
        c.DefaultRequestHeaders.AcceptLanguage.ParseAdd("en-US,en;q=0.9");
        return c;
    }

    public static bool Enabled
    {
        get
        {
            using var k = Registry.CurrentUser.OpenSubKey(RegKey);
            return k?.GetValue("HqArt") is not int v || v != 0;
        }
        set
        {
            using var k = Registry.CurrentUser.CreateSubKey(RegKey);
            k.SetValue("HqArt", value ? 1 : 0, RegistryValueKind.DWord);
        }
    }

    /// <summary>Returns full-size artwork bytes (JPEG/PNG) for the track, or null if nothing better was found.</summary>
    public static async Task<byte[]?> FindAsync(string title, string artist)
    {
        if (!Enabled || string.IsNullOrWhiteSpace(title)) return null;
        var key = Norm(title) + "|" + Norm(artist);
        if (Cache.TryGetValue(key, out var cached)) return cached;

        byte[]? result = null;
        try
        {
            string? id = null;
            try { id = await Task.Run(() => FindVideoIdInActiveTab(title)); }
            catch (Exception e) { Log.Write($"hq art: tab lookup failed: {e.Message}"); }
            if (id != null) Log.Write($"hq art: active tab is video {id}");

            id ??= await SearchYoutubeAsync(title, artist);
            if (id != null) result = await DownloadYoutubeThumbAsync(id);
            result ??= await ItunesAsync(title, artist);
        }
        catch (Exception e)
        {
            Log.Write($"hq art: failed: {e.Message}");
        }

        if (result == null) Log.Write($"hq art: nothing better found for '{title}'");
        Cache[key] = result;
        return result;
    }

    // ---- 1. active tab ------------------------------------------------------------------------------------

    static string? FindVideoIdInActiveTab(string mediaTitle)
    {
        foreach (var p in Process.GetProcesses())
        {
            try
            {
                if (!Browsers.Contains(p.ProcessName, StringComparer.OrdinalIgnoreCase)) continue;
                if (p.MainWindowHandle == IntPtr.Zero) continue;
                if (!p.MainWindowTitle.Contains(mediaTitle, StringComparison.OrdinalIgnoreCase)) continue;

                var root = AutomationElement.FromHandle(p.MainWindowHandle);
                var edits = root.FindAll(TreeScope.Descendants,
                    new PropertyCondition(AutomationElement.ControlTypeProperty, ControlType.Edit));
                foreach (AutomationElement edit in edits)
                {
                    if (!edit.TryGetCurrentPattern(ValuePattern.Pattern, out var pattern)) continue;
                    var url = ((ValuePattern)pattern).Current.Value;
                    if (url == null || !url.Contains("youtu", StringComparison.OrdinalIgnoreCase)) continue;
                    var m = VideoId().Match(url);
                    if (m.Success) return m.Groups[1].Value;
                }
            }
            catch
            {
                // Window closed or not accessible: try the next one.
            }
            finally
            {
                p.Dispose();
            }
        }
        return null;
    }

    // ---- 2. YouTube search --------------------------------------------------------------------------------

    /// <summary>Searches YouTube and accepts a result only if its title and channel both match the playing track.</summary>
    static async Task<string?> SearchYoutubeAsync(string title, string artist)
    {
        var query = Uri.EscapeDataString($"{title} {artist}".Trim());
        var html = await Http.GetStringAsync($"https://www.youtube.com/results?search_query={query}&hl=en&gl=US");
        var m = InitialData().Match(html);
        if (!m.Success)
        {
            Log.Write("hq art: YouTube search page had no data");
            return null;
        }

        using var doc = JsonDocument.Parse(m.Groups[1].Value);
        var wantTitle = Norm(title);
        var wantChannel = Norm(StripChannelNoise(artist));
        string? best = null;
        foreach (var v in FindAll(doc.RootElement, "videoRenderer"))
        {
            var id = v.TryGetProperty("videoId", out var idEl) ? idEl.GetString() : null;
            var t = Text(v, "title");
            var channel = Norm(StripChannelNoise(Text(v, "ownerText")));
            if (id == null || t == null) continue;

            var nt = Norm(t);
            var titleOk = nt == wantTitle || nt.Contains(wantTitle) || wantTitle.Contains(nt);
            var channelOk = wantChannel.Length == 0 || channel.Contains(wantChannel) || wantChannel.Contains(channel);
            if (titleOk && channelOk)
            {
                Log.Write($"hq art: YouTube match '{t}' ({channel}) -> {id}");
                best = id;
                break;
            }
        }
        if (best == null) Log.Write("hq art: no YouTube result matched title and channel");
        return best;
    }

    static string? Text(JsonElement el, string prop)
    {
        if (!el.TryGetProperty(prop, out var p)) return null;
        if (p.TryGetProperty("runs", out var runs) && runs.GetArrayLength() > 0 && runs[0].TryGetProperty("text", out var t)) return t.GetString();
        return p.TryGetProperty("simpleText", out var s) ? s.GetString() : null;
    }

    static IEnumerable<JsonElement> FindAll(JsonElement root, string name)
    {
        var stack = new Stack<JsonElement>();
        stack.Push(root);
        while (stack.Count > 0)
        {
            var e = stack.Pop();
            switch (e.ValueKind)
            {
                case JsonValueKind.Object:
                    foreach (var prop in e.EnumerateObject())
                    {
                        if (prop.Name == name) yield return prop.Value;
                        else stack.Push(prop.Value);
                    }
                    break;
                case JsonValueKind.Array:
                    foreach (var item in e.EnumerateArray()) stack.Push(item);
                    break;
            }
        }
    }

    static async Task<byte[]?> DownloadYoutubeThumbAsync(string id)
    {
        foreach (var name in new[] { "maxresdefault", "sddefault", "hqdefault" })
        {
            try
            {
                var bytes = await Http.GetByteArrayAsync($"https://i.ytimg.com/vi/{id}/{name}.jpg");
                if (bytes.Length > 4_000)
                {
                    Log.Write($"hq art: {name} for {id}, {bytes.Length / 1024} KB");
                    return bytes;
                }
            }
            catch (HttpRequestException)
            {
                // maxresdefault does not exist for every video; try the next size.
            }
        }
        return null;
    }

    // ---- 3. Apple music catalogue ---------------------------------------------------------------------------

    static async Task<byte[]?> ItunesAsync(string title, string artist)
    {
        if (string.IsNullOrWhiteSpace(artist)) return null;
        var term = Uri.EscapeDataString($"{StripChannelNoise(artist)} {title}");
        var json = await Http.GetStringAsync($"https://itunes.apple.com/search?term={term}&media=music&entity=song&limit=8");
        using var doc = JsonDocument.Parse(json);
        if (!doc.RootElement.TryGetProperty("results", out var results)) return null;

        var wantTitle = Norm(title);
        foreach (var r in results.EnumerateArray())
        {
            var name = r.TryGetProperty("trackName", out var n) ? n.GetString() : null;
            var art = r.TryGetProperty("artworkUrl100", out var a) ? a.GetString() : null;
            if (name == null || art == null) continue;
            var nn = Norm(name);
            if (!(nn == wantTitle || nn.Contains(wantTitle) || wantTitle.Contains(nn))) continue;

            foreach (var size in new[] { "1400x1400bb", "1000x1000bb", "600x600bb" })
            {
                try
                {
                    var bytes = await Http.GetByteArrayAsync(art.Replace("100x100bb", size));
                    if (bytes.Length > 4_000)
                    {
                        Log.Write($"hq art: Apple catalogue {size} for '{name}', {bytes.Length / 1024} KB");
                        return bytes;
                    }
                }
                catch (HttpRequestException)
                {
                    // Not available at this size.
                }
            }
        }
        return null;
    }

    // ---- helpers --------------------------------------------------------------------------------------------

    static string StripChannelNoise(string? s) =>
        Regex.Replace(s ?? "", @"\s*-\s*Topic$|VEVO$", "", RegexOptions.IgnoreCase).Trim();

    /// <summary>Lower-case letters and digits only, so punctuation and spacing differences do not matter.</summary>
    static string Norm(string? s) => Regex.Replace((s ?? "").ToLowerInvariant(), @"[^\p{L}\p{N}]+", "");
}
