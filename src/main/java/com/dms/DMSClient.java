package com.dms;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;



/**
 * 该客户端运行在给用户提供unix服务的服务器上。
 * 用来读取并收集该服务器上用户的上下线信息，并
 * 进行配对整理后发送给服务端汇总。
 * @author Administrator
 *
 */
public class DMSClient {
	//属性定义
	
	//第一步:解析日志所需属性
	
	private File logFile;//unix系统日志文件
	
	private File textLogFile;//保存解析后日志的文件
	
	private File lastPositionFile;//书签文件
	
	private int batch;//每次解析日志的条目数
	
	//第二步:配对日志所需要属性
	
	private File logRecFile;//保存配对日志的文件
	
	private File loginLogFile;//保存未配对日志的文件
	
	//第三步:发送日志所需要属性
	
	private String serverHost;//服务端地址
	
	private int serverPort;//服务端端口
	
	/**
	 * 构造方法，用来初始化客户端
	 * @throws Exception 
	 */
	public DMSClient() throws Exception{
		try {
			//1 解析配置文件config.xml
			Map<String,String> config	= loadConfig();
			//打桩
			System.out.println(config);
			//2 根据配置文件内容初始化属性
			init(config);
		} catch (Exception e) {
			System.out.println("初始化失败!");
			throw e;
		}
	}
	/**
	 * 构造方法初始化第二步,根据配置项初始化属性
	 * @param config
	 * @throws Exception 
	 */
	private void init(Map<String,String> config) throws Exception{
		try {
			logFile = new File(config.get("logfile"));
			textLogFile = new File(config.get("textlogfile"));
			lastPositionFile = new File(config.get("lastpositionfile"));
			batch = Integer.parseInt(config.get("batch"));
			logRecFile = new File(config.get("logrecfile"));
			loginLogFile = new File(config.get("loginlogfile"));
			serverHost = config.get("serverhost");
			serverPort = Integer.parseInt(config.get("serverport"));			
		} catch (Exception e) {
			System.out.println("初始化属性失败!");
			e.printStackTrace();
			throw e;
		}
	}
	
	
	/**
	 * 构造方法初始化第一步，解析配置文件
	 * @return 返回的Map中保存的是配置文件中的
	 *         每一条内容，其中key:标签的名字，
	 *         value为标签中间的文本
	 * @throws Exception 
	 */
	private Map<String,String> loadConfig() throws Exception{
		try {
			SAXReader reader = new SAXReader();
			Document doc = reader.read(new File("config.xml"));
			Element root = doc.getRootElement();
			Map<String,String> config = new HashMap<String,String>();
			/*
			 * 获取<config>标签中的所有子标签
			 * 并将每一个子标签的名字作为key,中间的
			 * 文本作为value存入Map集合
			 */
			List<Element> list = root.elements();
			for(Element e : list){
				String key = e.getName();
				String value = e.getTextTrim();
				config.put(key, value);
			}
			return config;
		} catch (Exception e) {
			System.out.println("解析配置文件异常!");
			e.printStackTrace();
			throw e;
		}
	}
	
	/**
	 * 客户端开始工作的方法
	 * 循环执行三步:
	 * 1:解析日志
	 * 2:配对日志
	 * 3:发送日志
	 */
	public void start(){
		parseLogs();
		matchLogs();
		sendLogs();
	}
	/**
	 * 第一步解析日志中的一个环节，
	 * 根据书签文件记录的位置判断是否还有
	 * 日志可以解析，若有，则将上次最后的位置
	 * 返回，若没有则返回-1。
	 * @return
	 */
	private long hasLogs(){
		try {
			/*
			 * 若lastPositionFile不存在，则说明
			 * 从来没有解析过，那么从头开始解析即可
			 */
			if(!lastPositionFile.exists()){
				return 0;
			}
			long lastPosition = IOUtil.readLong(lastPositionFile);
			
			if(logFile.length()-lastPosition>=LogData.LOG_LENGTH){
				return lastPosition;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return -1;
	}
	/**
	 * 第一步:解析日志
	 * @return true:解析成功
	 *         false:解析失败
	 */
	private boolean parseLogs(){
		/*
		 * 实现思路:
		 * 循环读取batch条日志，然后将每条日志中的
		 * 5个信息解析出来，最终组成一个字符串，以
		 * 行为单位，写入到textLogFile文件中
		 * 
		 * 实现步骤：
		 * 1:必要的判断工作
		 *   1.1:为了避免解析的日志还没有被使用，而
		 *       第一步又重复执行导致之前日志被覆盖
		 *       的问题，这里需要判断，若保存解析后
		 *       的日志文件存在，则第一步不再执行。
		 *       该日志文件会在第二步配对完毕后删除。
		 *   1.2:logFile文件必须存在(wtmpx文件)
		 *   1.3:是否还有日志可以解析
		 * 2:创建RandomAccessFile来读取logFile
		 * 3:将指针移动到上次最后读取的位置，准备
		 *   开始新的解析工作
		 * 4:解析工作
		 *   4.1:创建一个List集合，用于保存解析后
		 *       的每一条日志(LogData实例)
		 *   4.2:循环batch次，解析每条日志中的
		 *       5项内容(user,pid,type,time,host)
		 *       并用一个LogData实例保存，然后将
		 *       该LogData实例存入集合
		 * 5:将集合中的所有的日志以行为单位保存到
		 *   textLogFile中 
		 * 6:保存书签信息         
		 * 7:返回true,表示工作完毕
		 * 
		 */
		RandomAccessFile raf = null;
		try {
			if(textLogFile.exists()){
				return true;
			}
			if(!logFile.exists()){
				System.out.println(logFile+"不存在!");
				return false;
			}
			long lastPosition = hasLogs();
			//打桩
//			System.out.println(
//					"lastPosition:"+lastPosition
//			);
			
			if(lastPosition<0){
				System.out.println("没有日志可以解析了!");
				return false;
			}
			raf = new RandomAccessFile(logFile,"r");
			raf.seek(lastPosition);
			List<LogData> list = new ArrayList<LogData>();
			for(int i=0;i<batch;i++){
				//每次解析前都判断是否还有日志可以解析
				if(logFile.length()-lastPosition<LogData.LOG_LENGTH){
					break;
				}
				//解析user
				raf.seek(lastPosition+LogData.USER_OFFSET);
				String user = IOUtil.readString(raf, LogData.USER_LENGTH).trim();
				
				//解析PID
				raf.seek(lastPosition+LogData.PID_OFFSET);
				int pid = raf.readInt();
				
				//解析TYPE
				raf.seek(lastPosition+LogData.TYPE_OFFSET);
				short type = raf.readShort();
				
				//解析TIME
				raf.seek(lastPosition+LogData.TIME_OFFSET);
				int time = raf.readInt();
				
				//解析HOST
				raf.seek(lastPosition+LogData.HOST_OFFSET);
				String host = IOUtil.readString(raf, LogData.HOST_LENGTH).trim();
				LogData log = new LogData(user, pid, type, time, host);
				list.add(log);
				//打桩
//				System.out.println(log);
				
				//当解析完一条日志后，更新lastPosition
				lastPosition = raf.getFilePointer();
			}
			IOUtil.saveCollection(list, textLogFile);
			IOUtil.saveLong(lastPosition, lastPositionFile);
			return true;
		} catch (Exception e) {
			System.out.println("解析日志失败!");
			e.printStackTrace();
		} finally{
			if(raf != null){
				try {
					raf.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		return false;
	}
	/**
	 * 第二步:配对日志
	 * @return true:配对成功
	 *         false:配对失败
	 */
	private boolean matchLogs(){
		
			/*
			 * 实现思路:
			 * 将第一步解析的新日志，与上次为配对成功
			 * 的登入日志全部读取出来，然后再按照user,
			 * pid相同，type一个是7，一个是8进行配对。
			 * 只要能找到类型为8的，一定可以找到一个
			 * 能与之配对的登入日志。
			 * 
			 * 实现步骤:
			 * 1:必要的判断
			 *   1.1:logRecFile是否存在，存在则不再
			 *       进行新的配对工作，避免覆盖。
			 *   1.2:textLogFile文件必须存在。    
			 * 2:读取textLogFile将日志读取出来，并
			 *   存入到集合中。(若干LogData实例)
			 * 3:若loginLogFile文件若存在，则说明
			 *   有上次未配对成功的日志，也将其读取
			 *   出来存入集合等待一起配对
			 * 4:配对工作
			 *   4.1:创建一个集合，用于保存所有配对日志
			 *   4.2:创建两个Map分别保存登入日志与登出日志
			 *   4.3:遍历所有待配对的日志，按照登入与登出
			 *       分别存入两个Map中，
			 *       其中key:user,pid
			 *           value:LogData实例
			 *   4.4:遍历登出Map,并根据每条登出日志的key
			 *       去登入Map中找到对应的登入日志，并
			 *       以一个LogRec实例保存该配对日志，然后
			 *       存入配对日志的集合中。并将该配对日志
			 *       中的登入日志从登入Map中删除。这样一来
			 *       登入Map中应当只剩下没有配对的了。
			 *  5:将配对日志写入到logRecFile中
			 *  6:将所有未配对日志写入到loginLogFile中
			 *  7:将textLogFile文件删除
			 *  8:返回true,表示配对完毕
			 * 
			 */
		try {
			if(logRecFile.exists()){
				return true;
			}
			if(!textLogFile.exists()){
				System.out.println(textLogFile+"不存在!");
				return false;
			}
			List<LogData> list = IOUtil.loadLogData(textLogFile);
			if(loginLogFile.exists()){
				list.addAll(IOUtil.loadLogData(loginLogFile));
			}
			List<LogRec> matches = new ArrayList<LogRec>();
			Map<String,LogData> loginMap = new HashMap<String,LogData>();
			Map<String,LogData> logoutMap= new HashMap<String,LogData>();
			for(LogData logData : list){
				String key = logData.getUser()+","+logData.getPid();
				if(logData.getType()==LogData.TYPE_LOGIN){
					loginMap.put(key, logData);
				}else	if(logData.getType()==LogData.TYPE_LOGOUT){
					logoutMap.put(key, logData);
				}
			}
			Set<Entry<String,LogData>> entrySet = logoutMap.entrySet();
//			for(Entry<String,LogData> e : entrySet){
//				LogData logout = e.getValue();
//				LogData login = loginMap.remove(e.getKey());
//				LogRec logRec = new LogRec(login,logout);
//				matches.add(logRec);
//			}
			Iterator<Entry<String,LogData>> it = entrySet.iterator();
			while(it.hasNext()){
				Entry<String,LogData> eOutMap = it.next();
				String logoutMapKey = eOutMap.getKey();
				if(loginMap.containsKey(logoutMapKey)){
					LogData logIn = loginMap.get(logoutMapKey);
					LogData logOut = eOutMap.getValue();
					LogRec logRec = new LogRec(logIn,logOut);
					matches.add(logRec);
					loginMap.remove(logoutMapKey);
				}
			}
			IOUtil.saveCollection(matches, logRecFile);
			IOUtil.saveCollection(loginMap.values(),loginLogFile);
			textLogFile.delete();
			return true;
		} catch (Exception e) {
			System.out.println("配对日志失败!");
			e.printStackTrace();
		}
		return false;
	}
	/**
	 * 第三步:发送日志
	 * @return true:发送成功
	 *         false:发送失败
	 */
	private boolean sendLogs(){
			/*
			 * 实现思路:
			 * 将logRecFile文件中的所有配对日志读取
			 * 出来然后连接上服务端并发送过去，若服务端
			 * 全部接收，就可以将该文件删除，表示发送
			 * 完毕了。
			 * 实现步骤:
			 * 1:logRecFile文件必须存在
			 * 2:将所有配对日志读取出来并存入一个集合
			 *   等待发送
			 * 3:通过Socket连接服务端
			 * 4:创建输出流
			 * 5:顺序将所有配对日志按行发送给服务端
			 * 6:单独发送一个字符串"OVER"表示所有日志
			 *   均已发送完毕
			 * 7:创建输入流
			 * 8:读取服务端发送回来的响应字符串
			 * 9:若响应的字符串为"OK",表示服务端正常
			 *   接收了所有日志,这时就可以将logRecFile
			 *   文件删除并返回true表示发送完毕。
			 *       
			 */
		Socket socket = null;
		try {
			if(!logRecFile.exists()){
				System.out.println(logRecFile+"不存在!");
				return false;
			}
			List<String> matches = IOUtil.loadLogRec(logRecFile);
			socket = new Socket(serverHost,serverPort);
			OutputStream out = socket.getOutputStream();
			PrintWriter pw = new PrintWriter(new OutputStreamWriter(out,"UTF-8"));
			for(String log : matches){
				pw.println(log);
			}
			pw.println("OVER");
			pw.flush();
			InputStream in = socket.getInputStream();
			BufferedReader br = new BufferedReader(new InputStreamReader(in,"UTF-8"));
			String response = br.readLine();
			if("OK".equals(response)){
				logRecFile.delete();
				return true;
			}else{
				System.out.println("发送日志失败!");
				return false;
			}
			
		} catch (Exception e) {
			System.out.println("发送日志失败!");
			e.printStackTrace();
		} finally{
			if(socket != null){
				try {
					socket.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		return false;
	}
	
	public static void main(String[] args) {
		try {
			DMSClient client = new DMSClient();
			client.start();
		} catch (Exception e) {
			System.out.println("客户端运行失败!");
		}
	}
}








