package com.dms;
/**
 * 该类用于表示一条配对日志
 * 配对日志由两条登录日志组成:
 * 登入日志和登出日志
 * @author Administrator
 *
 */
public class LogRec {

	private LogData login;
	private LogData logout;
	public LogRec(LogData login, LogData logout) {
		super();
		this.login = login;
		this.logout = logout;
	}
	public LogData getLogin() {
		return login;
	}
	public void setLogin(LogData login) {
		this.login = login;
	}
	public LogData getLogout() {
		return logout;
	}
	public void setLogout(LogData logout) {
		this.logout = logout;
	}
	@Override
	public String toString() {
		return login + "|" + logout;
	}
	
}
