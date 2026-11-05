package com.owneragent.agent.react;

/**
 * 极简递归下降四则运算解析器。
 *
 * <p>只接受数字、小数点、空格以及 + - * / % ( )，刻意不使用脚本引擎，
 * 避免任意代码执行。</p>
 */
public final class Calculator {

    private final String input;
    private int pos;

    private Calculator(String input) {
        this.input = input;
    }

    public static double evaluate(String expression) {
        if (expression == null || expression.isBlank()) {
            throw new IllegalArgumentException("表达式不能为空");
        }
        Calculator parser = new Calculator(expression);
        double value = parser.parseExpression();
        parser.skipSpaces();
        if (parser.pos < parser.input.length()) {
            throw new IllegalArgumentException("无法解析的字符: " + parser.input.charAt(parser.pos));
        }
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("结果不是有限数值");
        }
        return value;
    }

    public static String format(double value) {
        if (value == Math.rint(value) && Math.abs(value) < 1e15) {
            return String.valueOf((long) value);
        }
        return String.valueOf(value);
    }

    private double parseExpression() {
        double value = parseTerm();
        while (true) {
            skipSpaces();
            if (consume('+')) {
                value += parseTerm();
            } else if (consume('-')) {
                value -= parseTerm();
            } else {
                return value;
            }
        }
    }

    private double parseTerm() {
        double value = parseFactor();
        while (true) {
            skipSpaces();
            if (consume('*')) {
                value *= parseFactor();
            } else if (consume('/')) {
                value /= requireNonZeroDivisor();
            } else if (consume('%')) {
                value %= requireNonZeroDivisor();
            } else {
                return value;
            }
        }
    }

    private double requireNonZeroDivisor() {
        double divisor = parseFactor();
        if (divisor == 0) {
            throw new IllegalArgumentException("除数不能为 0");
        }
        return divisor;
    }

    private double parseFactor() {
        skipSpaces();
        if (consume('+')) {
            return parseFactor();
        }
        if (consume('-')) {
            return -parseFactor();
        }
        if (consume('(')) {
            double value = parseExpression();
            skipSpaces();
            if (!consume(')')) {
                throw new IllegalArgumentException("缺少右括号");
            }
            return value;
        }
        return parseNumber();
    }

    private double parseNumber() {
        skipSpaces();
        int start = pos;
        boolean dotSeen = false;
        while (pos < input.length()) {
            char c = input.charAt(pos);
            if (Character.isDigit(c)) {
                pos++;
            } else if (c == '.' && !dotSeen) {
                dotSeen = true;
                pos++;
            } else {
                break;
            }
        }
        if (start == pos) {
            throw new IllegalArgumentException("此处需要数字或 '('");
        }
        String number = input.substring(start, pos);
        if (".".equals(number)) {
            throw new IllegalArgumentException("无效的数字: " + number);
        }
        return Double.parseDouble(number);
    }

    private void skipSpaces() {
        while (pos < input.length() && Character.isWhitespace(input.charAt(pos))) {
            pos++;
        }
    }

    private boolean consume(char expected) {
        if (pos < input.length() && input.charAt(pos) == expected) {
            pos++;
            return true;
        }
        return false;
    }
}
