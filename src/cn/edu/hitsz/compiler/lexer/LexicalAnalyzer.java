package cn.edu.hitsz.compiler.lexer;

import cn.edu.hitsz.compiler.NotImplementedException;
import cn.edu.hitsz.compiler.symtab.SymbolTable;
import cn.edu.hitsz.compiler.utils.FileUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.StreamSupport;
import java.util.Set;

/**
 * 实验一: 实现词法分析
 * <br>
 * 你可能需要参考的框架代码如下:
 *
 * @see Token 词法单元的实现
 * @see TokenKind 词法单元类型的实现
 */
public class LexicalAnalyzer {
    private final SymbolTable symbolTable;
    private String sourceCode;
    private final List<Token> tokens = new ArrayList<>();
    private final Set<String> keywords = Set.of("int", "return");

    public LexicalAnalyzer(SymbolTable symbolTable) {
        this.symbolTable = symbolTable;
    }

    /**
     * 从给予的路径中读取并加载文件内容
     *
     * @param path 路径
     */
    public void loadFile(String path) {
        this.sourceCode = FileUtils.readFile( path );
    }

    /**
     * 执行词法分析, 准备好用于返回的 token 列表 <br>
     * 需要维护实验一所需的符号表条目, 而得在语法分析中才能确定的符号表条目的成员可以先设置为 null
     * 源代码 → Token 流 + 符号表
     * 实现方法：DFA + 逐字符扫描
     */
    public void run() {

        int state = 0;
        
        // 拼接当前正在识别的单词 （标识符 / 数字）
        StringBuilder currentToken = new StringBuilder();

        char[] chars = this.sourceCode.toCharArray();

        for (int i = 0; i < chars.length; ++ i) {

            char ch = chars[i];

            switch ( state ) {
                // 状态 0 ：初始状态，准备识别一个新的 token
                case 0: {
                    if (! Character.isWhitespace( ch )) { // 跳过空白字符
                        // 是字母 -> 标识符或关键字，进入状态 1
                        if (Character.isLetter( ch )) {
                            currentToken.append( ch );
                            state = 1;
                        }
                        // 是数字 -> 整数常量，进入状态 2
                        else if (Character.isDigit( ch )) {
                            currentToken.append( ch );
                            state = 2;
                        } 
                        // 单字符运算符 / 分隔符，直接作为一个 Token 处理
                        else if (ch == '=') {
                            this.tokens.add(Token.simple(TokenKind.fromString("=")));
                        } else if (ch == '+') {
                            this.tokens.add(Token.simple(TokenKind.fromString("+")));
                        } else if (ch == '-') {
                            this.tokens.add(Token.simple(TokenKind.fromString("-")));
                        } else if (ch == '*') {
                            this.tokens.add(Token.simple(TokenKind.fromString("*")));
                        } else if (ch == '/') {
                            this.tokens.add(Token.simple(TokenKind.fromString("/")));
                        } else if (ch == ';') {
                            this.tokens.add(Token.simple(TokenKind.fromString("Semicolon")));
                        } else if (ch == '(') {
                            this.tokens.add(Token.simple(TokenKind.fromString("(")));
                        } else if (ch == ')') {
                            this.tokens.add(Token.simple(TokenKind.fromString(")")));
                        }
                        // 非法字符
                        else { System.err.println("Unknown character: " + ch); }
                    }
                    break;
                }
                // 状态 1 ：正在识别一个标识符 / 关键字
                case 1: {
                    if (Character.isLetterOrDigit( ch )) {
                        currentToken.append( ch );
                    }
                    // 标识符 / 关键字识别完成，判断是关键字还是标识符，并添加到 token 列表中
                    else {
                        String tokenStr = currentToken.toString();
                        if (this.keywords.contains( tokenStr )) {
                            this.tokens.add(Token.simple(TokenKind.fromString( tokenStr )));
                        } else {
                            this.tokens.add(Token.normal(TokenKind.fromString("id"), tokenStr));
                            if (! this.symbolTable.has( tokenStr )) {
                                this.symbolTable.add( tokenStr );
                            }
                        }
                        
                        // 清空缓冲区，回到初始状态，准备识别下一个 token
                        currentToken.setLength(0);
                        state = 0;
                        -- i;
                    }
                    break;
                }
                // 状态 2 ：正在识别一个整数常量
                case 2: {
                    if (Character.isDigit( ch )) {
                        currentToken.append( ch );
                    } else {
                        this.tokens.add(Token.normal(TokenKind.fromString("IntConst"), currentToken.toString()));
                        currentToken.setLength(0);
                        state = 0;
                        -- i;
                    }
                    break;
                }
                default:
                    throw new RuntimeException("Unexpected state: " + state);
            }
        }

        // 处理文件末尾时可能仍有未处理的 token
        if (currentToken.length() > 0) {
            if (state == 1) {
                String tokenStr = currentToken.toString();
                if (this.keywords.contains( tokenStr )) {
                    this.tokens.add(Token.simple(TokenKind.fromString( tokenStr )));
                } else {
                    this.tokens.add(Token.normal(TokenKind.fromString("id"), tokenStr));
                    if (! this.symbolTable.has( tokenStr )) {
                        this.symbolTable.add( tokenStr );
                    }
                }
            } else if (state == 2) {
                this.tokens.add(Token.normal(TokenKind.fromString("IntConst"), currentToken.toString()));
            }
        }

        // 添加文件结束标记，以便语法分析器识别输入结束
        this.tokens.add(Token.eof());
    }

    /**
     * 获得词法分析的结果, 保证在调用了 run 方法之后调用
     *
     * @return Token 列表
     */
    public Iterable<Token> getTokens() {
        return this.tokens;
    }

    public void dumpTokens(String path) {
        FileUtils.writeLines(
            path,
            StreamSupport.stream(getTokens().spliterator(), false).map(Token::toString).toList()
        );
    }


}
