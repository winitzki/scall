// Generated from /Users/sergei.winitzki/Code/scall/scall-core/src/main/resources/dhall.g4 by ANTLR 4.13.1
package io.chymyst.dhallg4;
import org.antlr.v4.runtime.atn.*;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.misc.*;
import org.antlr.v4.runtime.tree.*;
import java.util.List;
import java.util.Iterator;
import java.util.ArrayList;

@SuppressWarnings({"all", "warnings", "unchecked", "unused", "cast", "CheckReturnValue"})
public class dhallParser extends Parser {
	static { RuntimeMetaData.checkVersion("4.13.1", RuntimeMetaData.VERSION); }

	protected static final DFA[] _decisionToDFA;
	protected static final PredictionContextCache _sharedContextCache =
		new PredictionContextCache();
	public static final int
		T__0=1, T__1=2, T__2=3, T__3=4, T__4=5, INT=6;
	public static final int
		RULE_program = 0, RULE_expr = 1, RULE_x_minus = 2, RULE_x_plus = 3, RULE_x_times = 4, 
		RULE_x_other = 5;
	private static String[] makeRuleNames() {
		return new String[] {
			"program", "expr", "x_minus", "x_plus", "x_times", "x_other"
		};
	}
	public static final String[] ruleNames = makeRuleNames();

	private static String[] makeLiteralNames() {
		return new String[] {
			null, "'-'", "'+'", "'*'", "'('", "')'"
		};
	}
	private static final String[] _LITERAL_NAMES = makeLiteralNames();
	private static String[] makeSymbolicNames() {
		return new String[] {
			null, null, null, null, null, null, "INT"
		};
	}
	private static final String[] _SYMBOLIC_NAMES = makeSymbolicNames();
	public static final Vocabulary VOCABULARY = new VocabularyImpl(_LITERAL_NAMES, _SYMBOLIC_NAMES);

	/**
	 * @deprecated Use {@link #VOCABULARY} instead.
	 */
	@Deprecated
	public static final String[] tokenNames;
	static {
		tokenNames = new String[_SYMBOLIC_NAMES.length];
		for (int i = 0; i < tokenNames.length; i++) {
			tokenNames[i] = VOCABULARY.getLiteralName(i);
			if (tokenNames[i] == null) {
				tokenNames[i] = VOCABULARY.getSymbolicName(i);
			}

			if (tokenNames[i] == null) {
				tokenNames[i] = "<INVALID>";
			}
		}
	}

	@Override
	@Deprecated
	public String[] getTokenNames() {
		return tokenNames;
	}

	@Override

	public Vocabulary getVocabulary() {
		return VOCABULARY;
	}

	@Override
	public String getGrammarFileName() { return "dhall.g4"; }

	@Override
	public String[] getRuleNames() { return ruleNames; }

	@Override
	public String getSerializedATN() { return _serializedATN; }

	@Override
	public ATN getATN() { return _ATN; }

	public dhallParser(TokenStream input) {
		super(input);
		_interp = new ParserATNSimulator(this,_ATN,_decisionToDFA,_sharedContextCache);
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ProgramContext extends ParserRuleContext {
		public ExprContext expr() {
			return getRuleContext(ExprContext.class,0);
		}
		public TerminalNode EOF() { return getToken(dhallParser.EOF, 0); }
		public ProgramContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_program; }
	}

	public final ProgramContext program() throws RecognitionException {
		ProgramContext _localctx = new ProgramContext(_ctx, getState());
		enterRule(_localctx, 0, RULE_program);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(12);
			expr();
			setState(13);
			match(EOF);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ExprContext extends ParserRuleContext {
		public X_minusContext x_minus() {
			return getRuleContext(X_minusContext.class,0);
		}
		public X_plusContext x_plus() {
			return getRuleContext(X_plusContext.class,0);
		}
		public ExprContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_expr; }
	}

	public final ExprContext expr() throws RecognitionException {
		ExprContext _localctx = new ExprContext(_ctx, getState());
		enterRule(_localctx, 2, RULE_expr);
		try {
			setState(17);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,0,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(15);
				x_minus();
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(16);
				x_plus();
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class X_minusContext extends ParserRuleContext {
		public X_timesContext x_times() {
			return getRuleContext(X_timesContext.class,0);
		}
		public ExprContext expr() {
			return getRuleContext(ExprContext.class,0);
		}
		public X_minusContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_x_minus; }
	}

	public final X_minusContext x_minus() throws RecognitionException {
		X_minusContext _localctx = new X_minusContext(_ctx, getState());
		enterRule(_localctx, 4, RULE_x_minus);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(19);
			x_times();
			setState(20);
			match(T__0);
			setState(21);
			expr();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class X_plusContext extends ParserRuleContext {
		public X_timesContext x_times() {
			return getRuleContext(X_timesContext.class,0);
		}
		public List<ExprContext> expr() {
			return getRuleContexts(ExprContext.class);
		}
		public ExprContext expr(int i) {
			return getRuleContext(ExprContext.class,i);
		}
		public X_plusContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_x_plus; }
	}

	public final X_plusContext x_plus() throws RecognitionException {
		X_plusContext _localctx = new X_plusContext(_ctx, getState());
		enterRule(_localctx, 6, RULE_x_plus);
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(23);
			x_times();
			setState(28);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,1,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					{
					{
					setState(24);
					match(T__1);
					setState(25);
					expr();
					}
					} 
				}
				setState(30);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,1,_ctx);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class X_timesContext extends ParserRuleContext {
		public List<X_otherContext> x_other() {
			return getRuleContexts(X_otherContext.class);
		}
		public X_otherContext x_other(int i) {
			return getRuleContext(X_otherContext.class,i);
		}
		public X_timesContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_x_times; }
	}

	public final X_timesContext x_times() throws RecognitionException {
		X_timesContext _localctx = new X_timesContext(_ctx, getState());
		enterRule(_localctx, 8, RULE_x_times);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(31);
			x_other();
			setState(36);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==T__2) {
				{
				{
				setState(32);
				match(T__2);
				setState(33);
				x_other();
				}
				}
				setState(38);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class X_otherContext extends ParserRuleContext {
		public TerminalNode INT() { return getToken(dhallParser.INT, 0); }
		public ExprContext expr() {
			return getRuleContext(ExprContext.class,0);
		}
		public X_otherContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_x_other; }
	}

	public final X_otherContext x_other() throws RecognitionException {
		X_otherContext _localctx = new X_otherContext(_ctx, getState());
		enterRule(_localctx, 10, RULE_x_other);
		try {
			setState(44);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case INT:
				enterOuterAlt(_localctx, 1);
				{
				setState(39);
				match(INT);
				}
				break;
			case T__3:
				enterOuterAlt(_localctx, 2);
				{
				setState(40);
				match(T__3);
				setState(41);
				expr();
				setState(42);
				match(T__4);
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static final String _serializedATN =
		"\u0004\u0001\u0006/\u0002\u0000\u0007\u0000\u0002\u0001\u0007\u0001\u0002"+
		"\u0002\u0007\u0002\u0002\u0003\u0007\u0003\u0002\u0004\u0007\u0004\u0002"+
		"\u0005\u0007\u0005\u0001\u0000\u0001\u0000\u0001\u0000\u0001\u0001\u0001"+
		"\u0001\u0003\u0001\u0012\b\u0001\u0001\u0002\u0001\u0002\u0001\u0002\u0001"+
		"\u0002\u0001\u0003\u0001\u0003\u0001\u0003\u0005\u0003\u001b\b\u0003\n"+
		"\u0003\f\u0003\u001e\t\u0003\u0001\u0004\u0001\u0004\u0001\u0004\u0005"+
		"\u0004#\b\u0004\n\u0004\f\u0004&\t\u0004\u0001\u0005\u0001\u0005\u0001"+
		"\u0005\u0001\u0005\u0001\u0005\u0003\u0005-\b\u0005\u0001\u0005\u0000"+
		"\u0000\u0006\u0000\u0002\u0004\u0006\b\n\u0000\u0000,\u0000\f\u0001\u0000"+
		"\u0000\u0000\u0002\u0011\u0001\u0000\u0000\u0000\u0004\u0013\u0001\u0000"+
		"\u0000\u0000\u0006\u0017\u0001\u0000\u0000\u0000\b\u001f\u0001\u0000\u0000"+
		"\u0000\n,\u0001\u0000\u0000\u0000\f\r\u0003\u0002\u0001\u0000\r\u000e"+
		"\u0005\u0000\u0000\u0001\u000e\u0001\u0001\u0000\u0000\u0000\u000f\u0012"+
		"\u0003\u0004\u0002\u0000\u0010\u0012\u0003\u0006\u0003\u0000\u0011\u000f"+
		"\u0001\u0000\u0000\u0000\u0011\u0010\u0001\u0000\u0000\u0000\u0012\u0003"+
		"\u0001\u0000\u0000\u0000\u0013\u0014\u0003\b\u0004\u0000\u0014\u0015\u0005"+
		"\u0001\u0000\u0000\u0015\u0016\u0003\u0002\u0001\u0000\u0016\u0005\u0001"+
		"\u0000\u0000\u0000\u0017\u001c\u0003\b\u0004\u0000\u0018\u0019\u0005\u0002"+
		"\u0000\u0000\u0019\u001b\u0003\u0002\u0001\u0000\u001a\u0018\u0001\u0000"+
		"\u0000\u0000\u001b\u001e\u0001\u0000\u0000\u0000\u001c\u001a\u0001\u0000"+
		"\u0000\u0000\u001c\u001d\u0001\u0000\u0000\u0000\u001d\u0007\u0001\u0000"+
		"\u0000\u0000\u001e\u001c\u0001\u0000\u0000\u0000\u001f$\u0003\n\u0005"+
		"\u0000 !\u0005\u0003\u0000\u0000!#\u0003\n\u0005\u0000\" \u0001\u0000"+
		"\u0000\u0000#&\u0001\u0000\u0000\u0000$\"\u0001\u0000\u0000\u0000$%\u0001"+
		"\u0000\u0000\u0000%\t\u0001\u0000\u0000\u0000&$\u0001\u0000\u0000\u0000"+
		"\'-\u0005\u0006\u0000\u0000()\u0005\u0004\u0000\u0000)*\u0003\u0002\u0001"+
		"\u0000*+\u0005\u0005\u0000\u0000+-\u0001\u0000\u0000\u0000,\'\u0001\u0000"+
		"\u0000\u0000,(\u0001\u0000\u0000\u0000-\u000b\u0001\u0000\u0000\u0000"+
		"\u0004\u0011\u001c$,";
	public static final ATN _ATN =
		new ATNDeserializer().deserialize(_serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}
}