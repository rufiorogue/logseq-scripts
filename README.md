# logseq-scripts

A set of utilities for Logseq.

## generate-whiteboard-from-image-dir

### Overview

Enumerate image/video files in subdirectories and insert as blocks into a whiteboard.
Currently requires a valid whiteboard file (you may create just an empty one).
Also all existing blocks will be preserved.

### Usage

```
clojure -M:generate-whiteboard-from-image-dir --whiteboard-file <output.edn> --image-dir <media-directory>
```

Note that `media-directory` must be inside logseq graph (e.g. `./assets/...`) so the script can correctly calculate relative paths.