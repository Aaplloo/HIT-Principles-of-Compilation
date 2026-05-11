package cn.edu.hitsz.compiler.asm;

import cn.edu.hitsz.compiler.ir.IRImmediate;
import cn.edu.hitsz.compiler.ir.IRValue;
import cn.edu.hitsz.compiler.ir.IRVariable;
import cn.edu.hitsz.compiler.ir.Instruction;
import cn.edu.hitsz.compiler.utils.FileUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * TODO: 实验四: 实现汇编生成
 * <br>
 * 在编译器的整体框架中, 代码生成可以称作后端, 而前面的所有工作都可称为前端.
 * <br>
 * 在前端完成的所有工作中, 都是与目标平台无关的, 而后端的工作为将前端生成的目标平台无关信息
 * 根据目标平台生成汇编代码. 前后端的分离有利于实现编译器面向不同平台生成汇编代码. 由于前后
 * 端分离的原因, 有可能前端生成的中间代码并不符合目标平台的汇编代码特点. 具体到本项目你可以
 * 尝试加入一个方法将中间代码调整为更接近 risc-v 汇编的形式, 这样会有利于汇编代码的生成.
 * <br>
 * 为保证实现上的自由, 框架中并未对后端提供基建, 在具体实现时可自行设计相关数据结构.
 *
 * @see AssemblyGenerator#run() 代码生成与寄存器分配
 */
public class AssemblyGenerator {
    private static final List<String> REGISTERS = List.of("t0", "t1", "t2", "t3", "t4", "t5", "t6");

    private final List<Instruction> instructions = new ArrayList<>();
    private final List<String> assembly = new ArrayList<>();
    private final Map<IRVariable, Integer> lastUsage = new HashMap<>();
    private final Map<IRVariable, String> varToReg = new HashMap<>();
    private final Map<String, IRVariable> regToVar = new HashMap<>();
    private final Map<IRVariable, Integer> stackSlots = new HashMap<>();
    private final Set<IRVariable> memoryValid = new HashSet<>();
    private final Set<String> protectedRegisters = new HashSet<>();
    private int currentIndex = 0;

    /**
     * 加载前端提供的中间代码
     * <br>
     * 视具体实现而定, 在加载中或加载后会生成一些在代码生成中会用到的信息. 如变量的引用
     * 信息. 这些信息可以通过简单的映射维护, 或者自行增加记录信息的数据结构.
     *
     * @param originInstructions 前端提供的中间代码
     */
    public void loadIR(List<Instruction> originInstructions) {
        instructions.clear();
        assembly.clear();
        lastUsage.clear();
        varToReg.clear();
        regToVar.clear();
        stackSlots.clear();
        memoryValid.clear();
        protectedRegisters.clear();

        for (final var instruction : originInstructions) {
            normalizeInstruction(instruction);
        }

        for (var i = 0; i < instructions.size(); i++) {
            for (final var operand : instructions.get(i).getOperands()) {
                if (operand instanceof IRVariable variable) {
                    lastUsage.put(variable, i);
                }
            }
        }

        final var variables = new HashSet<IRVariable>();
        for (final var instruction : instructions) {
            final var result = getResultOrNull(instruction);
            if (result != null) {
                variables.add(result);
            }
            for (final var operand : instruction.getOperands()) {
                if (operand instanceof IRVariable variable) {
                    variables.add(variable);
                }
            }
        }

        variables.stream()
            .sorted(Comparator.comparing(IRVariable::getName))
            .forEach(variable -> stackSlots.put(variable, stackSlots.size()));
    }


    /**
     * 执行代码生成.
     * <br>
     * 根据理论课的做法, 在代码生成时同时完成寄存器分配的工作. 若你觉得这样的做法不好,
     * 也可以将寄存器分配和代码生成分开进行.
     * <br>
     * 提示: 寄存器分配中需要的信息较多, 关于全局的与代码生成过程无关的信息建议在代码生
     * 成前完成建立, 与代码生成的过程相关的信息可自行设计数据结构进行记录并动态维护.
     */
    public void run() {
        assembly.clear();
        assembly.add(".text");

        final var frameSize = stackSlots.size() * 4;
        if (frameSize > 0) {
            assembly.add("    addi sp, sp, -" + frameSize);
        }

        for (currentIndex = 0; currentIndex < instructions.size(); currentIndex++) {
            final var instruction = instructions.get(currentIndex);
            switch (instruction.getKind()) {
                case MOV -> genMov(instruction);
                case ADD, SUB, MUL -> genBinary(instruction);
                case RET -> genReturn(instruction);
            }
            recycleDeadVariables(instruction);
        }
    }


    /**
     * 输出汇编代码到文件
     *
     * @param path 输出文件路径
     */
    public void dump(String path) {
        FileUtils.writeLines(path, assembly);
    }

    private void normalizeInstruction(Instruction instruction) {
        switch (instruction.getKind()) {
            case ADD -> normalizeAdd(instruction);
            case SUB -> normalizeSub(instruction);
            case MUL -> normalizeMul(instruction);
            case MOV, RET -> instructions.add(instruction);
        }
    }

    private void normalizeAdd(Instruction instruction) {
        final var result = instruction.getResult();
        final var lhs = instruction.getLHS();
        final var rhs = instruction.getRHS();

        if (lhs instanceof IRImmediate left && rhs instanceof IRImmediate right) {
            instructions.add(Instruction.createMov(result, IRImmediate.of(left.getValue() + right.getValue())));
        } else if (lhs instanceof IRImmediate) {
            instructions.add(Instruction.createAdd(result, rhs, lhs));
        } else {
            instructions.add(instruction);
        }
    }

    private void normalizeSub(Instruction instruction) {
        final var result = instruction.getResult();
        final var lhs = instruction.getLHS();
        final var rhs = instruction.getRHS();

        if (lhs instanceof IRImmediate left && rhs instanceof IRImmediate right) {
            instructions.add(Instruction.createMov(result, IRImmediate.of(left.getValue() - right.getValue())));
        } else if (lhs instanceof IRImmediate) {
            final var temp = IRVariable.temp();
            instructions.add(Instruction.createMov(temp, lhs));
            instructions.add(Instruction.createSub(result, temp, rhs));
        } else {
            instructions.add(instruction);
        }
    }

    private void normalizeMul(Instruction instruction) {
        final var result = instruction.getResult();
        final var lhs = instruction.getLHS();
        final var rhs = instruction.getRHS();

        if (lhs instanceof IRImmediate left && rhs instanceof IRImmediate right) {
            instructions.add(Instruction.createMov(result, IRImmediate.of(left.getValue() * right.getValue())));
        } else if (lhs instanceof IRImmediate) {
            final var temp = IRVariable.temp();
            instructions.add(Instruction.createMov(temp, lhs));
            instructions.add(Instruction.createMul(result, temp, rhs));
        } else if (rhs instanceof IRImmediate) {
            final var temp = IRVariable.temp();
            instructions.add(Instruction.createMov(temp, rhs));
            instructions.add(Instruction.createMul(result, lhs, temp));
        } else {
            instructions.add(instruction);
        }
    }

    private void genMov(Instruction instruction) {
        final var result = instruction.getResult();
        final var from = instruction.getFrom();
        protectedRegisters.clear();

        if (from instanceof IRImmediate immediate) {
            final var resultReg = ensureResultRegister(result);
            emit("li " + resultReg + ", " + immediate.getValue(), instruction);
        } else {
            final var fromReg = ensureValueRegister(from);
            protectedRegisters.add(fromReg);
            final var resultReg = ensureResultRegister(result);
            emit("mv " + resultReg + ", " + fromReg, instruction);
        }

        protectedRegisters.clear();
        memoryValid.remove(result);
    }

    private void genBinary(Instruction instruction) {
        protectedRegisters.clear();
        final var lhsReg = ensureValueRegister(instruction.getLHS());
        protectedRegisters.add(lhsReg);
        final var kind = instruction.getKind();
        final var rhs = instruction.getRHS();

        if (rhs instanceof IRImmediate immediate && kind != cn.edu.hitsz.compiler.ir.InstructionKind.MUL) {
            final var resultReg = ensureResultRegister(instruction.getResult());
            final var value = kind == cn.edu.hitsz.compiler.ir.InstructionKind.SUB
                ? -immediate.getValue()
                : immediate.getValue();
            emit("addi " + resultReg + ", " + lhsReg + ", " + value, instruction);
        } else {
            final var rhsReg = ensureValueRegister(rhs);
            protectedRegisters.add(rhsReg);
            final var resultReg = ensureResultRegister(instruction.getResult());
            final var op = switch (kind) {
                case ADD -> "add";
                case SUB -> "sub";
                case MUL -> "mul";
                default -> throw new RuntimeException("Unsupported binary instruction " + kind);
            };
            emit(op + " " + resultReg + ", " + lhsReg + ", " + rhsReg, instruction);
        }

        protectedRegisters.clear();
        memoryValid.remove(instruction.getResult());
    }

    private void genReturn(Instruction instruction) {
        final var returnValue = instruction.getReturnValue();
        if (returnValue instanceof IRImmediate immediate) {
            emit("li a0, " + immediate.getValue(), instruction);
        } else {
            final var returnReg = ensureValueRegister(returnValue);
            emit("mv a0, " + returnReg, instruction);
        }
    }

    private String ensureValueRegister(IRValue value) {
        if (value instanceof IRVariable variable) {
            if (varToReg.containsKey(variable)) {
                return varToReg.get(variable);
            }

            final var reg = allocateRegister(variable);
            if (memoryValid.contains(variable)) {
                emitRaw("lw " + reg + ", " + stackOffset(variable) + "(sp)");
            }
            return reg;
        }

        throw new RuntimeException("Immediate values cannot be loaded without an instruction context");
    }

    private String ensureResultRegister(IRVariable variable) {
        if (varToReg.containsKey(variable)) {
            return varToReg.get(variable);
        }

        return allocateRegister(variable);
    }

    private String allocateRegister(IRVariable variable) {
        for (final var register : REGISTERS) {
            if (!protectedRegisters.contains(register) && !regToVar.containsKey(register)) {
                bind(variable, register);
                return register;
            }
        }

        for (final var register : REGISTERS) {
            final var occupied = regToVar.get(register);
            if (!protectedRegisters.contains(register) && lastUsage.getOrDefault(occupied, -1) <= currentIndex) {
                unbind(occupied, false);
                bind(variable, register);
                return register;
            }
        }

        final var victim = REGISTERS.stream()
            .filter(register -> !protectedRegisters.contains(register))
            .map(regToVar::get)
            .max(Comparator.comparingInt(value -> lastUsage.getOrDefault(value, Integer.MAX_VALUE)))
            .orElseThrow();
        final var register = varToReg.get(victim);
        spill(victim);
        unbind(victim, false);
        bind(variable, register);
        return register;
    }

    private void bind(IRVariable variable, String register) {
        final var oldVariable = regToVar.get(register);
        if (oldVariable != null) {
            varToReg.remove(oldVariable);
        }

        final var oldRegister = varToReg.get(variable);
        if (oldRegister != null) {
            regToVar.remove(oldRegister);
        }

        varToReg.put(variable, register);
        regToVar.put(register, variable);
    }

    private void unbind(IRVariable variable, boolean store) {
        if (store) {
            spill(variable);
        }

        final var register = varToReg.remove(variable);
        if (register != null) {
            regToVar.remove(register);
        }
    }

    private void spill(IRVariable variable) {
        final var register = varToReg.get(variable);
        if (register != null && !memoryValid.contains(variable)) {
            emitRaw("sw " + register + ", " + stackOffset(variable) + "(sp)");
            memoryValid.add(variable);
        }
    }

    private void recycleDeadVariables(Instruction instruction) {
        final var result = getResultOrNull(instruction);
        for (final var operand : instruction.getOperands()) {
            if (operand instanceof IRVariable variable
                && !variable.equals(result)
                && lastUsage.getOrDefault(variable, -1) == currentIndex) {
                unbind(variable, false);
            }
        }
    }

    private IRVariable getResultOrNull(Instruction instruction) {
        return instruction.getKind().isBinary() || instruction.getKind().isUnary()
            ? instruction.getResult()
            : null;
    }

    private int stackOffset(IRVariable variable) {
        return stackSlots.get(variable) * 4;
    }

    private void emit(String code, Instruction instruction) {
        assembly.add("    " + code + "\t\t#  " + instruction);
    }

    private void emitRaw(String code) {
        assembly.add("    " + code);
    }
}
