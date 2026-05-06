package cn.edu.hitsz.compiler.parser;

import java.util.Stack;

import cn.edu.hitsz.compiler.lexer.Token;
import cn.edu.hitsz.compiler.parser.table.Production;
import cn.edu.hitsz.compiler.parser.table.Status;
import cn.edu.hitsz.compiler.symtab.SourceCodeType;
import cn.edu.hitsz.compiler.symtab.SymbolTable;
import cn.edu.hitsz.compiler.symtab.SymbolTableEntry;

// 实验三: 实现语义分析
public class SemanticAnalyzer implements ActionObserver {

    private SymbolTable symbolTable;

    private final Stack<String> typeStack = new Stack<>(); // 存储类型信息
    private final Stack<String> valueStack = new Stack<>(); // 存储表达式的值
    private int tempVarCounter = 0; // 临时变量计数器

    @Override
    public void whenAccept(Status currentStatus) {
        // 该过程在遇到 Accept 时要采取的代码动作
        System.out.println("语义分析器已经接收成功");
    }

    @Override
    public void whenReduce(Status currentStatus, Production production) {
        // 该过程在遇到 reduce production 时要采取的代码动作
        switch (production.toString()) {
            case "D -> int": {
                // 将 SourceCodeType.Int 类型压入栈
                typeStack.push("int");
                break;
            }
            case "S -> D id": {
                String id = valueStack.pop();
                String type = typeStack.pop();
                final SymbolTableEntry entry = symbolTable.get(id);

                if (entry.getType() != null) {
                    throw new RuntimeException("重复声明错误: " + id);
                }

                if ("int".equals( type )) {
                    entry.setType( SourceCodeType.Int );
                }

                break;
            }
            case "S -> id = E": {
                String exprValue = valueStack.pop(); // 右侧表达式
                String idToAssign = valueStack.pop(); // 左侧变量

                // 检查左侧变量是否已声明
                if (!symbolTable.has( idToAssign ) || symbolTable.get( idToAssign ).getType() == null) {
                    throw new RuntimeException("未声明的标识符: " + idToAssign);
                }

                // 检查右侧表达式的符号表，排除对临时变量的检查
                if (!isNumeric( exprValue ) && !exprValue.startsWith("$")
                    && (!symbolTable.has( exprValue ) || symbolTable.get( exprValue ).getType() == null)) {
                    throw new RuntimeException("未声明的标识符: " + exprValue);
                }
                break;
            }
            case "S -> return E": {
                String returnValue = valueStack.pop();
                generateCode("RET " + returnValue);
                break;
            }
            case "E -> E + A":
            case "E -> E - A":
            case "E -> E & A":
            case "E -> E / A": {
                String rightOperand = valueStack.pop();
                String leftOperand = valueStack.pop();
                String operator = production.body().get(1).toString(); // 获取操作符
                String tempVar = getNextTempVar();
                generateCode(tempVar + " = " + leftOperand + " " + operator + " " + rightOperand);
                valueStack.push(tempVar);
                break;
            }
            case "A -> A * B": {
                String b = valueStack.pop();
                String a = valueStack.pop();
                String tempMulVar = getNextTempVar();
                generateCode(tempMulVar + " = " + a + " * " + b);
                valueStack.push( tempMulVar );
                break;
            }
            case "B -> - B": {
                String value = valueStack.pop();
                String tempNegVar = getNextTempVar();
                generateCode(tempNegVar + " = 0 - " + value);
                valueStack.push( tempNegVar );
                break;
            }
            case "B -> id":
            case "B -> IntConst":
                // 改为从 valueStack 中获取当前处理的值，不使用 currentToken
                String value = valueStack.peek(); // 或者根据语法规则使用 pop()
                break;

            default: break;
        }
    }

    @Override
    public void whenShift(Status currentStatus, Token currentToken) {
        // 该过程在遇到 shift 时要采取的代码动作
        if (currentToken.getKind().getIdentifier().equals("id")) {
            valueStack.push( currentToken.getText() ); // 标识符压入栈
        } else if (currentToken.getKind().getIdentifier().equals("IntConst")) {
            valueStack.push( currentToken.getText() ); // 常量压入栈
        }
    }

    @Override
    public void setSymbolTable(SymbolTable table) {
        // 设计你可能需要的符号表存储结构
        // 如果需要使用符号表的话, 可以将它或者它的一部分信息存起来, 比如使用一个成员变量存储
        this.symbolTable = table;
    }

    // 辅助方法：用于生成中间代码
    private void generateCode(String code) {
    }

    // 获取下一个临时变量名
    private String getNextTempVar() {
        return "$t" + (tempVarCounter ++);
    }

    // 判断是否是数字
    private boolean isNumeric(String str) {
        try {
            Integer.parseInt( str );
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
