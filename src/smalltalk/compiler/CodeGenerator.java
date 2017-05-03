package smalltalk.compiler;

import org.antlr.symtab.ClassSymbol;
import org.antlr.symtab.Scope;
import org.antlr.symtab.Symbol;
import org.antlr.symtab.Utils;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.TerminalNode;
import smalltalk.compiler.symbols.*;

import java.util.ArrayList;
import java.util.List;

/** Fill STBlock, STMethod objects in Symbol table with bytecode,
 * {@link STCompiledBlock}.
 */
public class CodeGenerator extends SmalltalkBaseVisitor<Code> {
	public static final boolean dumpCode = false;

	public STClass currentClassScope;
	public Scope currentScope;

	/** With which compiler are we generating code? */
	public final Compiler compiler;

	public CodeGenerator(Compiler compiler) {
		this.compiler = compiler;
	}

	/** This and defaultResult() critical to getting code to bubble up the
	 *  visitor call stack when we don't implement every method.
	 */
	@Override
	protected Code aggregateResult(Code aggregate, Code nextResult) {
		if ( aggregate!=Code.None ) {
			if ( nextResult!=Code.None ) {
				return aggregate.join(nextResult);
			}
			return aggregate;
		}
		else {
			return nextResult;
		}
	}

	@Override
	protected Code defaultResult() {
		return Code.None;
	}

	@Override
	public Code visitFile(SmalltalkParser.FileContext ctx) {
		currentScope = compiler.symtab.GLOBALS;
		visitChildren(ctx);
		return Code.None;
	}

	@Override
	public Code visitClassDef(SmalltalkParser.ClassDefContext ctx) {
		currentClassScope = ctx.scope;
		pushScope(ctx.scope);
		Code code = visitChildren(ctx);
		popScope();
		currentClassScope = null;
		return code;
	}

    @Override
	public Code visitMain(SmalltalkParser.MainContext ctx) {
		currentScope = ctx.scope;
		currentClassScope = ctx.classScope;
		pushScope(ctx.scope);
		Code code = visitChildren(ctx);
		STCompiledBlock stCompiledBlock = new STCompiledBlock(ctx.classScope, (STBlock) currentScope);
		ctx.scope.compiledBlock = stCompiledBlock;
        code = aggregateResult(code, Code.of(Bytecode.POP));
        code = aggregateResult(code, Code.of(Bytecode.SELF));
        code = aggregateResult(code, Code.of(Bytecode.RETURN));
        ctx.scope.compiledBlock.bytecode = code.bytes();
		popScope();
		return code;
	}

	public STCompiledBlock getCompiledPrimitive(STPrimitiveMethod primitive) {
		STCompiledBlock compiledMethod = new STCompiledBlock(currentClassScope, primitive);
		return compiledMethod;
	}

	@Override
	public Code visitKeywordSend(SmalltalkParser.KeywordSendContext ctx) {
		Code code = visit(ctx.recv);
		for(SmalltalkParser.BinaryExpressionContext e :	 ctx.args){
			code = aggregateResult(code,visit(e));
		}
		currentClassScope.stringTable.add(ctx.KEYWORD(0).getText());
		return code;
	}

    @Override
    public Code visitBinaryExpression(SmalltalkParser.BinaryExpressionContext ctx) {
        Code code = visit(ctx.unaryExpression(0));
        return code;
    }

    @Override
    public Code visitUnaryIsPrimary(SmalltalkParser.UnaryIsPrimaryContext ctx) {
        Code code = visit(ctx.primary());
        return code;
    }

    /**
	 All expressions have values. Must pop each expression value off, except
	 last one, which is the block return value. Visit method for blocks will
	 issue block_return instruction. Visit method for method will issue
	 pop self return.  If last expression is ^expr, the block_return or
	 pop self return is dead code but it is always there as a failsafe.

	 localVars? expr ('.' expr)* '.'?
	 */
	@Override
	public Code visitFullBody(SmalltalkParser.FullBodyContext ctx) {
		// fill in
		Code code = defaultResult();
        for(SmalltalkParser.StatContext statContext : ctx.stat()){
            Code c = visit(statContext);
            code = aggregateResult(c, code);
        }
		return code;
	}

    @Override
    public Code visitId(SmalltalkParser.IdContext ctx) {
        Code code = defaultResult();
        return code;
    }

    @Override
    public Code visitLiteral(SmalltalkParser.LiteralContext ctx) {
        Code code = defaultResult();
        return code;
    }

    @Override
	public Code visitReturn(SmalltalkParser.ReturnContext ctx) {
		Code e = visit(ctx.messageExpression());
		if ( compiler.genDbg ) {
			e = Code.join(e, dbg(ctx.start)); // put dbg after expression as that is when it executes
		}
		Code code = e.join(Compiler.method_return());
		return code;
	}

	public void pushScope(Scope scope) {
		currentScope = scope;
	}

	public void popScope() {
//		if ( currentScope.getEnclosingScope()!=null ) {
//			System.out.println("popping from " + currentScope.getScopeName() + " to " + currentScope.getEnclosingScope().getScopeName());
//		}
//		else {
//			System.out.println("popping from " + currentScope.getScopeName() + " to null");
//		}
		currentScope = currentScope.getEnclosingScope();
	}

	public int getLiteralIndex(String s) {
		return 0;
	}

	public Code dbgAtEndMain(Token t) {
		int charPos = t.getCharPositionInLine() + t.getText().length();
		return dbg(t.getLine(), charPos);
	}

	public Code dbgAtEndBlock(Token t) {
		int charPos = t.getCharPositionInLine() + t.getText().length();
		charPos -= 1; // point at ']'
		return dbg(t.getLine(), charPos);
	}

	public Code dbg(Token t) {
		return dbg(t.getLine(), t.getCharPositionInLine());
	}

	public Code dbg(int line, int charPos) {
		return Compiler.dbg(getLiteralIndex(compiler.getFileName()), line, charPos);
	}

	public Code store(String id) {
		return null;
	}

	public Code push(String id) {
		return null;
	}

	public Code sendKeywordMsg(ParserRuleContext receiver,
							   Code receiverCode,
							   List<SmalltalkParser.BinaryExpressionContext> args,
							   List<TerminalNode> keywords)
	{
		return null;
	}

	public String getProgramSourceForSubtree(ParserRuleContext ctx) {
		return null;
	}
}
