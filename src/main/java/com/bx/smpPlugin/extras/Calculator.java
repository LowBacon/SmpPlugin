package com.bx.smpPlugin.extras;

import java.util.Locale;

/**
 * A tiny calculator for /calc: numbers, + - * / % ^, brackets, unary minus, and sqrt, abs, round,
 * floor, ceil, min, max, pi, e. It parses by hand (no script engine), so a player can never run code.
 * "x" and "×" mean multiply and "÷" divide, so a stack count like "64x9" works. Commas are thousands
 * separators ("1,000"); min and max take two values separated by ";", e.g. max(3;7).
 */
public final class Calculator {

    private final String input;
    private int pos;

    private Calculator(String input) {
        this.input = input.toLowerCase(Locale.ROOT).replace('×', '*').replace('÷', '/').replace(",", "");
    }

    /** Evaluates an expression or throws IllegalArgumentException with a short reason. */
    public static double evaluate(String expression) {
        if (expression == null || expression.isBlank() || expression.length() > 200) {
            throw new IllegalArgumentException("empty or too long");
        }
        Calculator calculator = new Calculator(expression);
        double value = calculator.expression();
        calculator.skipSpaces();
        if (calculator.pos < calculator.input.length()) {
            throw new IllegalArgumentException("unexpected '" + calculator.input.charAt(calculator.pos) + "'");
        }
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new IllegalArgumentException("not a number");
        }
        return value;
    }

    /** Formats a result without a trailing ".0" and with at most 6 decimals. */
    public static String format(double value) {
        if (value == Math.rint(value) && Math.abs(value) < 1e15) {
            return String.format(Locale.ROOT, "%,d", (long) value);
        }
        String text = String.format(Locale.ROOT, "%,.6f", value);
        return text.replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private void skipSpaces() {
        while (pos < input.length() && Character.isWhitespace(input.charAt(pos))) {
            pos++;
        }
    }

    private boolean eat(char expected) {
        skipSpaces();
        if (pos < input.length() && input.charAt(pos) == expected) {
            pos++;
            return true;
        }
        return false;
    }

    private double expression() {
        double value = term();
        while (true) {
            if (eat('+')) {
                value += term();
            } else if (eat('-')) {
                value -= term();
            } else {
                return value;
            }
        }
    }

    private double term() {
        double value = power();
        while (true) {
            if (eat('*') || eat('x')) {
                value *= power();
            } else if (eat('/')) {
                double divisor = power();
                if (divisor == 0D) {
                    throw new IllegalArgumentException("division by zero");
                }
                value /= divisor;
            } else if (eat('%')) {
                double divisor = power();
                if (divisor == 0D) {
                    throw new IllegalArgumentException("division by zero");
                }
                value %= divisor;
            } else {
                return value;
            }
        }
    }

    private double power() {
        double base = unary();
        if (eat('^')) {
            return Math.pow(base, power()); // right-associative
        }
        return base;
    }

    private double unary() {
        if (eat('-')) {
            return -unary();
        }
        if (eat('+')) {
            return unary();
        }
        return primary();
    }

    private double primary() {
        if (eat('(')) {
            double value = expression();
            if (!eat(')')) {
                throw new IllegalArgumentException("missing )");
            }
            return value;
        }
        skipSpaces();
        int start = pos;
        if (pos < input.length() && Character.isLetter(input.charAt(pos))) {
            while (pos < input.length() && Character.isLetter(input.charAt(pos))) {
                pos++;
            }
            String name = input.substring(start, pos);
            switch (name) {
                case "pi":
                    return Math.PI;
                case "e":
                    return Math.E;
                default:
                    break;
            }
            if (!eat('(')) {
                throw new IllegalArgumentException("unknown word '" + name + "'");
            }
            double first = expression();
            double second = Double.NaN;
            if (eat(';')) {
                second = expression();
            }
            if (!eat(')')) {
                throw new IllegalArgumentException("missing )");
            }
            return switch (name) {
                case "sqrt" -> Math.sqrt(first);
                case "abs" -> Math.abs(first);
                case "round" -> Math.round(first);
                case "floor" -> Math.floor(first);
                case "ceil" -> Math.ceil(first);
                case "min" -> Math.min(first, second);
                case "max" -> Math.max(first, second);
                default -> throw new IllegalArgumentException("unknown function '" + name + "'");
            };
        }
        while (pos < input.length() && (Character.isDigit(input.charAt(pos)) || input.charAt(pos) == '.')) {
            pos++;
        }
        if (start == pos) {
            throw new IllegalArgumentException(pos < input.length() ? "unexpected '" + input.charAt(pos) + "'" : "unexpected end");
        }
        double number = Double.parseDouble(input.substring(start, pos));
        // Suffixes: 1k, 2.5m, 1b
        if (pos < input.length()) {
            char suffix = input.charAt(pos);
            double factor = switch (suffix) {
                case 'k' -> 1e3;
                case 'm' -> 1e6;
                case 'b' -> 1e9;
                default -> 1D;
            };
            if (factor != 1D) {
                pos++;
                number *= factor;
            }
        }
        return number;
    }
}
