# Chapter test fixtures

Tiny silent 4 s files with three chapters: "Opening" 0–1000 ms, "Middle Part" 1000–2500 ms, "Ending" 2500–4000 ms.

| File | Chapter source |
|---|---|
| `fx.m4b` | QuickTime chapter track + Nero `chpl` |
| `fx_qt_only.m4b` | QuickTime chapter track only (`-movflags +disable_chpl`) |
| `fx_chpl_only.m4b` | Nero `chpl` only (`tref/chap` renamed to `xhap`, so the chapter track is ignored) |
| `fx.mp3` | ID3v2.3 `CHAP` + `CTOC` |

Recipe (`ch.txt` is an ffmetadata file with the three `[CHAPTER]` blocks above):

```sh
ffmpeg -nostdin -f lavfi -i anullsrc=r=8000:cl=mono -i ch.txt -map_metadata 1 -map_chapters 1 \
  -t 4 -c:a aac -b:a 8k fx.m4b
ffmpeg -nostdin -f lavfi -i anullsrc=r=8000:cl=mono -i ch.txt -map_metadata 1 -map_chapters 1 \
  -t 4 -c:a libmp3lame -b:a 8k -id3v2_version 3 fx.mp3
ffmpeg -nostdin -f lavfi -i anullsrc=r=8000:cl=mono -i ch.txt -map 0:a -map_metadata 1 -map_chapters 1 \
  -t 4 -c:a aac -b:a 8k -movflags +disable_chpl -f mp4 fx_qt_only.m4b
# chpl-only: copy fx.m4b and replace the 4-byte box type "tref" ... "chap" with "xhap".
```
