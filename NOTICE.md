# Third-party notices

BambooCEF is MIT-licensed (see `LICENSE`). The Chromium runtime it downloads
is built from the projects below; their license texts ship inside the runtime
archive as `LICENSE-cef.txt` and `LICENSE-java-cef.txt`, next to the
binaries they cover.

## Chromium Embedded Framework (CEF)

https://github.com/chromiumembedded/cef — the binary distribution
`cef_binary_146.0.10+g8219561+chromium-146.0.7680.179` provides `libcef.dll`,
the helper process and the resource packs.

```
Copyright (c) 2008-2020 Marshall A. Greenblatt. Portions Copyright (c)
2006-2009 Google Inc. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

   * Redistributions of source code must retain the above copyright
     notice, this list of conditions and the following disclaimer.
   * Redistributions in binary form must reproduce the above copyright
     notice, this list of conditions and the following disclaimer in the
     documentation and/or other materials provided with the distribution.
   * Neither the name of Google Inc. nor the name Chromium Embedded
     Framework nor the names of its contributors may be used to endorse
     or promote products derived from this software without specific prior
     written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
"AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
```

## java-cef (JCEF)

https://github.com/chromiumembedded/java-cef — the Java bindings, built
from upstream with the patches in `tools/runtime/patches/` and shipped as
`jcef.jar` plus its native helper library.

```
Copyright (c) 2008-2013 Marshall A. Greenblatt. Portions Copyright (c)
2006-2009 Google Inc. All rights reserved.
```

Licensed under the same BSD 3-Clause terms as CEF above; the full text is
`LICENSE-java-cef.txt` in the runtime archive.

## Chromium

CEF embeds Chromium, https://www.chromium.org/, licensed under the BSD
3-Clause License (https://chromium.googlesource.com/chromium/src/+/HEAD/LICENSE).
Components inside Chromium carry their own open-source licenses; the
authoritative list is Chromium's `about:credits` page, reachable from any
BambooCEF surface with DevTools enabled.
