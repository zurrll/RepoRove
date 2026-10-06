"""Bundle this prototype's Vite build into one portable HTML file."""

import argparse
import base64
import re
from pathlib import Path


def export(output: Path) -> None:
    root = Path(__file__).resolve().parent.parent
    dist = root / "dist"
    entry = dist / "index.html"
    if not entry.is_file():
        raise SystemExit("Build first with: npm run build")
    html = entry.read_text(encoding="utf-8")

    def read_asset(url: str) -> str:
        path = (dist / url.lstrip("/")).resolve()
        if not path.is_relative_to(dist.resolve()) or not path.is_file():
            raise SystemExit(f"Unsupported build asset: {url}")
        return path.read_text(encoding="utf-8")

    def script(match: re.Match[str]) -> str:
        code = read_asset(match.group(1))
        code = re.sub(r"</script", r"<\\/script", code, flags=re.IGNORECASE)
        return '<script type="module">' + code + "</script>"

    def stylesheet(match: re.Match[str]) -> str:
        css = read_asset(match.group(1))
        css = re.sub(r"</style", r"<\\/style", css, flags=re.IGNORECASE)
        return "<style>" + css + "</style>"

    html, js_count = re.subn(
        r'<script[^>]*\bsrc="([^"]+)"[^>]*>\s*</script>', script, html
    )
    html, css_count = re.subn(
        r'<link\b[^>]*rel="stylesheet"[^>]*href="([^"]+)"[^>]*>',
        stylesheet,
        html,
    )
    favicon = base64.b64encode((dist / "favicon.svg").read_bytes()).decode("ascii")
    html = html.replace('href="/favicon.svg"', f'href="data:image/svg+xml;base64,{favicon}"')
    if js_count != 1 or css_count != 1:
        raise SystemExit("Expected exactly one JavaScript and one CSS bundle; review exporter.")
    if re.search(r'<script[^>]*\bsrc=|<link[^>]*rel="(?:stylesheet|modulepreload)"', html):
        raise SystemExit("External build dependencies remain; review exporter.")
    if re.search(r'(?:url\([\s\'"]*(?!data:)[^)]*\)|\bimport\s*\()', read_asset(
        re.search(r'/assets/[^" ]+\.css', entry.read_text(encoding="utf-8")).group(0)
    )):
        raise SystemExit("CSS has external assets; review exporter.")
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(html, encoding="utf-8")
    print(f"Exported {output.resolve()} ({output.stat().st_size:,} bytes)")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", required=True, type=Path)
    export(parser.parse_args().output)
