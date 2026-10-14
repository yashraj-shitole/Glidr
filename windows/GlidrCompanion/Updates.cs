using System.Reflection;
using System.Text.Json;

namespace GlidrCompanion;

/// <summary>Latest Glidr release on GitHub: the companion .exe for this PC and the APK for the phone share one tag.</summary>
sealed record GlidrRelease(string Version, string Page, string? ExeUrl, string? ApkUrl);

/// <summary>
/// "Check for updates": asks GitHub for the latest published release. It only notifies and links to the download;
/// nothing is downloaded or installed automatically.
/// </summary>
static class Updates
{
    const string Repo = "yashraj-shitole/Glidr";
    static readonly HttpClient Http = CreateClient();

    public static string Current { get; } = FormatVersion(Assembly.GetExecutingAssembly().GetName().Version);

    static HttpClient CreateClient()
    {
        var c = new HttpClient { Timeout = TimeSpan.FromSeconds(15) };
        c.DefaultRequestHeaders.UserAgent.ParseAdd($"GlidrCompanion/{FormatVersion(Assembly.GetExecutingAssembly().GetName().Version)}");
        c.DefaultRequestHeaders.Accept.ParseAdd("application/vnd.github+json");
        return c;
    }

    static string FormatVersion(Version? v) => v == null ? "0.0.0" : $"{v.Major}.{v.Minor}.{Math.Max(0, v.Build)}";

    /// <summary>Latest release, or null when the repo has none yet. Throws on network or HTTP errors.</summary>
    public static async Task<GlidrRelease?> LatestAsync()
    {
        using var res = await Http.GetAsync($"https://api.github.com/repos/{Repo}/releases/latest");
        if (res.StatusCode == System.Net.HttpStatusCode.NotFound) return null;
        res.EnsureSuccessStatusCode();
        using var doc = JsonDocument.Parse(await res.Content.ReadAsStringAsync());
        var root = doc.RootElement;
        var tag = root.TryGetProperty("tag_name", out var t) ? t.GetString() ?? "" : "";
        var version = tag.TrimStart('v', 'V');
        if (version.Length == 0) throw new InvalidOperationException("release has no tag");
        var page = root.TryGetProperty("html_url", out var h) && IsTrusted(h.GetString()) ? h.GetString()! : $"https://github.com/{Repo}/releases/latest";
        string? exe = null, apk = null;
        if (root.TryGetProperty("assets", out var assets))
        {
            foreach (var a in assets.EnumerateArray())
            {
                var name = a.TryGetProperty("name", out var n) ? n.GetString() ?? "" : "";
                var url = a.TryGetProperty("browser_download_url", out var u) ? u.GetString() : null;
                if (!IsTrusted(url)) continue;
                if (exe == null && name.EndsWith(".exe", StringComparison.OrdinalIgnoreCase)) exe = url;
                if (apk == null && name.EndsWith(".apk", StringComparison.OrdinalIgnoreCase)) apk = url;
            }
        }
        return new GlidrRelease(version, page, exe, apk);
    }

    /// <summary>> 0 when <paramref name="a"/> is newer than <paramref name="b"/> ("v1.2.3", "1.2.3-beta").</summary>
    public static int Compare(string a, string b)
    {
        static int[] Parse(string v) => v.Trim().TrimStart('v', 'V').Split('.', '-', '+').Take(3)
            .Select(p => int.TryParse(p, out var n) ? n : 0).ToArray();
        var x = Parse(a);
        var y = Parse(b);
        for (var i = 0; i < 3; i++)
        {
            var d = (i < x.Length ? x[i] : 0) - (i < y.Length ? y[i] : 0);
            if (d != 0) return d;
        }
        return 0;
    }

    static bool IsTrusted(string? url) => url != null && url.StartsWith("https://github.com/", StringComparison.Ordinal);

    /// <summary>The line sent to the phone so it learns about updates over Bluetooth, even without internet itself.</summary>
    public static string PhoneMessage(GlidrRelease r) => JsonSerializer.Serialize(new Dictionary<string, string?>
    {
        ["t"] = "release",
        ["v"] = r.Version,
        ["apk"] = r.ApkUrl,
        ["page"] = r.Page,
    });
}
