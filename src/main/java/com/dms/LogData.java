package com.dms;
/**
 * 该类用于描述wtmpx文件中的每一条日志
 * 这里只记录该项目需要的5项信息:
 * user,pid,type,time,host
 *
 */
public class LogData {
	public static final int LOG_LENGTH = 372;//日志的长度(字节量)
	public static final int USER_LENGTH = 32;//user在一条日志中的起始位置
	public static final int USER_OFFSET = 0;//user在一条日志中的长度(字节量)
	public static final int PID_OFFSET = 68;//pid在一条日志中的起始位置
	public static final int TYPE_OFFSET = 72;//type在一条日志中的起始位置
	public static final int TIME_OFFSET = 80;//time在一条日志中的起始位置
	public static final int HOST_OFFSET = 114;//host在一条日志中的起始位置
	public static final int HOST_LENGTH = 258;//host在一条日志中的长度(字节量)
	public static final short TYPE_LOGIN = 7;//日志类型:登入日志
	public static final short TYPE_LOGOUT = 8;//日志类型:登入日志
	private String user;//用户名
	private int pid;//进程ID
	private short type;//日志类型
	private int time;//日志生成时间
	private String host;//用户地址
	public LogData(String user, int pid, short type, int time, String host) {
		super();
		this.user = user;
		this.pid = pid;
		this.type = type;
		this.time = time;
		this.host = host;
	}
	public LogData() {
		
	}
	/**
	 * 将给定的字符串解析为一个LogData实例
	 * 该字符串格式必须是当前类toString方法
	 * 生成的格式
	 * lidz,441232,7,1375334515,192.168.1.61
	 */
	public LogData(String logData){
		String[] data = logData.split(",");
		this.user = data[0];
		this.pid = Integer.parseInt(data[1]);
		this.type = Short.parseShort(data[2]);
		this.time = Integer.parseInt(data[3]);
		this.host = data[4];
	}
	public String getUser() {
		return user;
	}
	public void setUser(String user) {
		this.user = user;
	}
	public int getPid() {
		return pid;
	}
	public void setPid(int pid) {
		this.pid = pid;
	}
	public short getType() {
		return type;
	}
	public void setType(short type) {
		this.type = type;
	}
	public int getTime() {
		return time;
	}
	public void setTime(int time) {
		this.time = time;
	}
	public String getHost() {
		return host;
	}
	public void setHost(String host) {
		this.host = host;
	}
	@Override
	public String toString() {
		return user + "," + pid + "," + type + "," + time + "," + host;
	}
}
