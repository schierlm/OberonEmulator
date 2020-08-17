#!/bin/sh
set -e
cat >offline-emu.html <<'EOF'
<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<script>
var offlineInfo = {config: {images:[]}, rom: new Int32Array([
EOF

perl -- - boot.rom >>offline-emu.html <<'EOF'
  open($f,'<', $ARGV[0]) or die $!;
  binmode $f; $sep='';
  while(!eof($f)) {
    if (read($f,$b,4) != 4) { die $!; }
    $hex = sprintf "0x%02X%02X%02X%02X", ord(substr($b,3,1)), ord(substr($b,2,1)),
        ord(substr($b,1,1)), ord(substr($b,0,1));
    $hex =~ s/^0x00+/0x0/;
    print "$sep$hex";
    $sep=',';
  }
EOF

echo '])};' >>offline-emu.html
cat risc.js webdriver.js >>offline-emu.html
echo '</script>' >>offline-emu.html
sed '1,4d' < emu.html | grep -v '<script' >>offline-emu.html

