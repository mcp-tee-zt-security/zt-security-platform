package com.zt.security.policy;

import java.util.*;

/**
 * Policy DSL v2 parser.  The production decision path deliberately does not
 * depend on regex extraction: malformed or partially parsed policies fail
 * closed instead of silently becoming broader policies.
 */
public record PolicyDsl(
        String name,
        String effect,
        int priority,
        String description,
        String mode,
        List<String> tags,
        List<Rule> rules,
        Condition condition) {

    public record Rule(String left, String op, String right) {
    }

    public sealed interface Expr permits Comparison, And, Or, Not {
    }
    public record Comparison(String left, String op, Object right) implements Expr {
    }
    public record And(List<Expr> terms) implements Expr {
    }
    public record Or(List<Expr> terms) implements Expr {
    }
    public record Not(Expr term) implements Expr {
    }

    public static PolicyDsl parse(String text) {
        if (text == null || text.isBlank()) throw new PolicyParseException("Policy text is empty");
        return new Parser(text).parse();
    }

    public record Condition(Expr expression) {
    }

    // Compatibility accessors for the existing security graph/governance views.
    public String principalType() {
        return firstRule("principal.type", "==");
    }
    public String action() {
        return firstRule("action", "==");
    }
    public String resourceType() {
        return firstRule("resource.type", "==");
    }
    private String firstRule(String left, String op) {
        return rules.stream().filter(r -> left.equals(r.left()) && op.equals(r.op())).map(Rule::right).findFirst().orElse(null);
    }

    public static final class PolicyParseException extends IllegalArgumentException {
        public PolicyParseException(String message) {
            super(message);
        }
    }

    private enum Type {
        IDENT, STRING, NUMBER, BOOLEAN, LBRACE, RBRACE, LBRACKET, RBRACKET,
        LPAREN, RPAREN, COMMA, EQ, NE, GE, LE, GT, LT, IN, CONTAINS, AND, OR, NOT, EOF }

    private record Token(Type type, String text, int position) {
    }

    private static final class Lexer {
        private final String s;
        private int p;
        Lexer(String s) {
            this.s = s;
        }
        List<Token> lex() {
            List<Token> out = new ArrayList<>();
            while (p < s.length()) {
                char c=s.charAt(p);
                if (Character.isWhitespace(c)) {
                    p++;
                    continue;
                }
                if (c=='/' && p+1<s.length() && s.charAt(p+1)=='/') {
                    while(p<s.length()&&s.charAt(p)!='\n')p++;
                    continue;
                    }
                int start=p;
                if (c=='"') {
                    out.add(new Token(Type.STRING, readString(), start));
                    continue;
                }
                if (Character.isLetter(c)||c=='_') {
                    String w=readWord();
                    Type t=switch(w){
                        case "in"->Type.IN;
                        case "contains"->Type.CONTAINS;
                        case "and"->Type.AND;
                        case "or"->Type.OR;
                        case "not"->Type.NOT;
                        case "true",
                        "false"->Type.BOOLEAN;
                        default->Type.IDENT;
                        }
                        ;
                    out.add(new Token(t,w,start));
                    continue;
                }
                if (c=='-' || Character.isDigit(c)) {
                    out.add(new Token(Type.NUMBER,readNumber(),start));
                    continue;
                }
                Type t=switch(c){
                    case '{'->Type.LBRACE;
                        case '}'->Type.RBRACE;
                    case '['->Type.LBRACKET;
                    case ']'->Type.RBRACKET;
                    case '('->Type.LPAREN;
                    case ')'->Type.RPAREN;
                    case ','->Type.COMMA;
                    default->null;
                    }
                    ;
                if(t!=null){
                    p++;
                    out.add(new Token(t,String.valueOf(c),start));
                    continue;
                }
                String op = p+1<s.length()?s.substring(p,p+2):"";
                Type ot=switch(op){
                    case "=="->Type.EQ;
                    case "!="->Type.NE;
                    case ">="->Type.GE;
                    case "<="->Type.LE;
                    default->null;
                    }
                    ;
                if(ot!=null){
                    p+=2;
                    out.add(new Token(ot,op,start));
                    continue;
                }
                if(c=='>'||c=='<'){
                    p++;
                    out.add(new Token(c=='>'?Type.GT:Type.LT,String.valueOf(c),start));
                    continue;
                }
                throw error("Unexpected character '"+c+"'",start);
            }
            out.add(new Token(Type.EOF,"",p));
            return out;
        }
        private String readString(){
            p++;
            StringBuilder b=new StringBuilder();
            while(p<s.length()) {
                char c=s.charAt(p++);
                if(c=='"')return b.toString();
                if(c=='\\'&&p<s.length()){
                    char n=s.charAt(p++);
                    b.append(switch(n){
                        case 'n'->'\n';
                        case 'r'->'\r';
                        case 't'->'\t';
                        default->n;
                        }
                        );
                        }
                        else b.append(c);
                        }
            throw error("Unterminated string",p);
        }
        private String readWord(){
            int st=p++;
            while(p<s.length()&&(Character.isLetterOrDigit(s.charAt(p))||
            s.charAt(p)=='_'||s.charAt(p)=='.'||s.charAt(p)==':'||s.charAt(p)=='-'))p++;
            return s.substring(st,p);
            }
        private String readNumber(){
            int st=p++;
            while(p<s.length()&&(Character.isDigit(s.charAt(p))||
            s.charAt(p)=='.'))p++;
            return s.substring(st,p);
            }
        private PolicyParseException error(String m,int at){
            return new PolicyParseException(m+" at position "+at);
        }
    }

    private static final class Parser {
        private final List<Token> ts;
        private int i;
        Parser(String s){
            ts=new Lexer(s).lex();
        }
        PolicyDsl parse(){
            expectWord("policy");
            String name=expect(Type.STRING).text();
            expect(Type.LBRACE);
            int priority=100;
            String effect=null, description=null, mode=null;
            List<String> tags=new ArrayList<>();
            List<Rule> rules=new ArrayList<>();
            Condition cond=null;
            while(!peek(Type.RBRACE)){
                if(matchWord("priority")){
                    priority=number(expect(Type.NUMBER));
                    continue;
                }
                if(matchWord("effect")){
                    String e=expect(Type.IDENT).text();
                    if(!Set.of("allow","deny","step_up").contains(e))throw error("Invalid effect: "+e,
                    previous().position());
                    effect=e;
                    continue;
                    }
                if(matchWord("description")){
                    description=expect(Type.STRING).text();
                    continue;
                }
                if(matchWord("mode")){
                    mode=expect(Type.STRING).text();
                    continue;
                }
                if(matchWord("tags")){
                    tags.addAll(stringList());
                    continue;
                }
                if(matchWord("condition")){
                    expect(Type.LBRACE);
                    Expr e=peek(Type.RBRACE)?null:parseOr();
                    expect(Type.RBRACE);
                    cond=e==null?null:new Condition(e);
                    continue;
                    }
                if(peek(Type.IDENT)){
                    String left=expect(Type.IDENT).text();
                    String op=operator();
                    Object right=valueOrList();
                    if(!Set.of("principal.type","action","resource.type").contains(left)) throw
error("Only principal.type, action and resource.type are" +
" valid match clauses; use condition for '"+
                    left+"'",
                    previous().position());
                    rules.add(new Rule(left,op,render(right)));
                    continue;
                }
                throw error("Unexpected token '"+peek().text()+"'",peek().position());
            }
            expect(Type.RBRACE);
            expect(Type.EOF);
            if(effect==null) throw error("Missing effect",0);
            if(rules.isEmpty()) throw error("At least one principal/action/resource match clause is required",0);
            return new PolicyDsl(name,effect,priority,description,mode,List.copyOf(tags),List.copyOf(rules),cond);
        }
        Expr parseOr(){
            List<Expr> x=new ArrayList<>();
            x.add(parseAnd());
            while(match(Type.OR))x.add(parseAnd());
            return x.size()==1?x.get(0):new Or(List.copyOf(x));
        }
        Expr parseAnd(){
            List<Expr> x=new ArrayList<>();
            x.add(parseUnary());
            while(match(Type.AND))x.add(parseUnary());
            return x.size()==1?x.get(0):new And(List.copyOf(x));
        }
        Expr parseUnary(){
            if(match(Type.NOT))return new Not(parseUnary());
            if(match(Type.LPAREN)){
                Expr e=parseOr();
                expect(Type.RPAREN);
                return e;
            }
            return new Comparison(expect(Type.IDENT).text(),operator(),valueOrList());
        }
        String operator(){
            Token t=peek();
            Type ty=t.type();
            if(EnumSet.of(Type.EQ,
            Type.NE,Type.GE,Type.LE,Type.GT,Type.LT,Type.IN,Type.CONTAINS).contains(ty)){
                i++;
                return t.text();
                }
                throw error("Expected comparison operator",t.position());
        }
        Object valueOrList(){
            if(match(Type.LBRACKET)){
                List<Object> x=new ArrayList<>();
                if(!peek(Type.RBRACKET)){
                    x.add(value());
                    while(match(Type.COMMA))x.add(value());
                }
                expect(Type.RBRACKET);
                return List.copyOf(x);
                }
                return value();
                }
        Object value(){
            Token t=peek();
            if(match(Type.STRING))return t.text();
            if(match(Type.NUMBER))return t.text().contains(".")?Double.valueOf(t.text()):Long.valueOf(t.text());
            if(match(Type.BOOLEAN))return Boolean.valueOf(t.text());
            throw error("Expected value",
            t.position());
            }
        List<String> stringList(){
            Object v=valueOrList();
            if(!(v instanceof List<?
            > l)||l.stream().anyMatch(x->!(x instanceof String)))throw error("tags must be a list of strings",
            previous().position());
            return l.stream().map(String.class::cast).toList();
        }
        String render(Object v){
            if(v instanceof List<?> l)return l.toString();
            return String.valueOf(v);
        }
        int number(Token t){
            try{
                return Integer.parseInt(t.text());
            }
        catch(Exception e){
                throw error("priority must be an integer",t.position());
                }
                }
        boolean match(Type t){
            if(peek(t)){
                i++;
                return true;
            }
        return false;
        }
        boolean matchWord(String w){
            if(peek(Type.IDENT)&&peek().text().equals(w)){
            i++;
            return true;
            }
            return false;
            }
        void expectWord(String w){
            if(!matchWord(w))throw error("Expected '"+w+"'",peek().position());
        }
        Token expect(Type t){
            if(!peek(t))throw error("Expected "+t+" but found '"+peek().text()+"'",
            peek().position());
            return ts.get(i++);
            }
        boolean peek(Type t){
            return ts.get(i).type()==t;
        }
        Token peek(){
            return ts.get(i);
            }
            Token previous(){
                return ts.get(Math.max(0,i-1));
            }
        PolicyParseException error(String m,int at){
            return new PolicyParseException(m+" at position "+at);
        }
    }
}
