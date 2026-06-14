#!/usr/bin/env bash
# Fetch the multilingual-e5-small ONNX model. It is ~118MB (over GitHub's 100MB
# file limit) so it is not committed; run this once before building.
# The tokenizer vocab (e5_vocab.tsv) IS committed.
set -euo pipefail
DIR="$(cd "$(dirname "$0")" && pwd)/src/main/resources/model"
mkdir -p "$DIR"
echo "downloading e5-small.onnx (~118MB) ..."
curl -sSL "https://huggingface.co/Xenova/multilingual-e5-small/resolve/main/onnx/model_quantized.onnx" \
  -o "$DIR/e5-small.onnx"
echo "done: $DIR/e5-small.onnx ($(du -h "$DIR/e5-small.onnx" | cut -f1))"
