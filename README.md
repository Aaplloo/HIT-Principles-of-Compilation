# HIT Principles of Compilation Labs

哈尔滨工业大学《编译原理》实验模板与实现代码。

## 项目结构

- `src/`: 编译器源码
  - `lexer/`: 词法分析
  - `parser/`: 语法分析、语义分析与 IR 生成
  - `ir/`: 中间表示
  - `asm/`: RISC-V 目标代码生成
  - `utils/`: 文件路径、IR 模拟执行等工具
- `data/in/`: 输入程序、LR 分析表、文法和编码表
- `data/out/`: 程序运行后的输出文件
- `data/std/`: 标准输出参考
- `scripts/`: 辅助检查脚本

## 实验四：目标代码生成

本仓库已实现 `AssemblyGenerator`，支持将实验三生成的 IR 转换为 RISC-V 汇编。

主要功能：

- IR 规范化：处理立即数运算、左立即数 `ADD/SUB/MUL`、右立即数 `MUL` 等情况
- 目标代码生成：支持 `MOV`、`ADD`、`SUB`、`MUL`、`RET`
- 寄存器分配：使用 `t0` 到 `t6`
- 寄存器回收：根据变量最后一次使用位置释放寄存器
- 溢出处理：寄存器不足时将变量写入栈槽，需要时再加载
- 汇编输出：生成 `data/out/assembly_language.asm`

## 编译与运行

在仓库根目录执行：

```powershell
javac -encoding UTF-8 -d out (Get-ChildItem -Recurse -Filter *.java src | ForEach-Object { $_.FullName })
java -cp out cn.edu.hitsz.compiler.Main
```

生成的汇编文件位于：

```text
data/out/assembly_language.asm
```

## 使用 RARS 验证

将 `rars.jar` 路径替换为本机实际路径：

```powershell
java -jar path\to\rars.jar mc CompactDataAtZero a0 nc dec ae255 data\out\assembly_language.asm
```

程序返回值会显示在 `a0` 中，可与 `data/out/ir_emulate_result.txt` 对比。
