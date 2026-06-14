#!/usr/bin/env bash
# Shrink the packaged Mule app jar by stripping the non-linux-x64 ONNX Runtime
# native libs (win .pdb alone is ~300MB uncompressed). CloudHub runs linux-x64.
set -euo pipefail
JAR="${1:?usage: strip-onnx-natives.sh <app.jar>}"
ORT_PATH="repository/com/microsoft/onnxruntime/onnxruntime/1.19.2/onnxruntime-1.19.2.jar"
tmp="$(mktemp -d)"
unzip -q -o "$JAR" "$ORT_PATH" -d "$tmp"
zip -q -d "$tmp/$ORT_PATH" \
  'ai/onnxruntime/native/win-x64/*' \
  'ai/onnxruntime/native/osx-x64/*' \
  'ai/onnxruntime/native/osx-aarch64/*' \
  'ai/onnxruntime/native/linux-aarch64/*' >/dev/null 2>&1 || true
( cd "$tmp" && zip -q "$OLDPWD/$JAR" "$ORT_PATH" )
rm -rf "$tmp"
echo "stripped onnxruntime natives -> linux-x64 only"

# Also drop the model binary duplicated into META-INF/mule-src by attachMuleSources
# (a 118MB binary is pointless as "source"; Java sources stay viewable).
zip -q -d "$1" \
  'META-INF/mule-src/mule-clouderby/src/main/resources/model/e5-small.onnx' \
  'META-INF/mule-src/mule-clouderby/src/main/resources/model/e5_vocab.tsv' >/dev/null 2>&1 || true
echo "stripped mule-src model duplicate"
