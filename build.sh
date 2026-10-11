#!/usr/bin/env bash
cd "$(dirname "$0")"

echo "=========================================="
echo "  真正的按住疾跑  --  编译脚本"
echo "=========================================="
echo

if ! command -v java >/dev/null 2>&1; then
  echo "[X] 没检测到 Java。请先安装 JDK 25："
  echo "    https://adoptium.net/temurin/releases/?version=25"
  exit 1
fi
echo "[1/3] Java 版本："
java -version 2>&1 | head -1
echo

if ! command -v gradle >/dev/null 2>&1; then
  echo "[X] 没检测到 gradle 命令，请确认它已加入 PATH。"
  exit 1
fi
echo "[2/3] Gradle 版本："
gradle --version 2>/dev/null | grep '^Gradle'
echo

echo "[3/3] 开始编译（第一次要下载 Minecraft 26.3，5~15 分钟，请耐心等）"
echo
gradle build --no-daemon

echo
if [ $? -eq 0 ]; then
  echo "=========================================="
  echo "  编译成功！jar 文件在："
  ls -1 build/libs/*.jar 2>/dev/null
  echo
  echo "  把它复制到 .minecraft/mods/ 文件夹里就行了。"
  echo "=========================================="
else
  echo "=========================================="
  echo "  编译失败了。请把上面红色的报错发给我。"
  echo "=========================================="
fi
