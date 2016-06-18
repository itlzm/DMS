package com.dms;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
/**
 * DMS服务端，用来接收每个客户端发送过来的
 * 配对日志并保存在本地文件中
 * @author Administrator
 *
 */
public class DMSServer {
	//属性定义
	private ServerSocket server;//用来接收客户端连接的服务端的ServerSocket
	private ExecutorService threadPool;//用来管理处理客户端请求的线程的线程池
	private File serverLogFile;//保存所有客户端发送过来配对日志的文件
	private BlockingQueue<String> messageQueue = new LinkedBlockingQueue<String>();//消息队列
	public DMSServer() throws Exception{
		try {
			System.out.println("服务端正在初始化...");
			//1 解析配置文件server-config.xml
			Map<String,String> config = loadConfig();
			
			//2 根据配置文件内容初始化属性
			init(config);
			System.out.println("服务端初始化完毕...");
		} catch (Exception e) {
			System.out.println("初始化服务端失败!");
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
	public Map<String,String> loadConfig() throws Exception{
		try{
			
			SAXReader sax = new SAXReader();
			Document doc = sax.read(new File("server-config.xml"));
			Element root = doc.getRootElement();
			Map<String,String> config = new HashMap<String,String>();
			List<Element> elements = root.elements();
			for(Element e:elements){
				String key = e.getName();
				String value = e.getText().trim();
				config.put(key, value);
			}
			return config;
		}catch(Exception e){
			e.printStackTrace();
			System.out.println("解析配置文件异常!");
			throw e;
		}
	}
	/**
	 * 构造方法初始化第二步,根据配置项初始化属性
	 * @param config
	 * @throws Exception 
	 */
	public void init(Map<String,String> config) throws Exception{
		/*
		 * 用配置文件中的<logrecfile>初始化属性：serverLogFile
		 * 用配置文件中的<threadsum>初始化属性：threadPool，这里创建固定大小线程池。该值作为线程池线程数量
		 * 用配置文件中的<serverport>初始化属性：server,这里这个值为ServerSocket的服务端口
		 */
			this.serverLogFile = new File(config.get("logrecfile"));
			this.server = new ServerSocket(Integer.parseInt(config.get("serverport")));
			this.threadPool = Executors.newFixedThreadPool(Integer.parseInt(config.get("threadsum")));
	}
	/**
	 * 服务端开始工作的方法
	 * @throws Exception
	 */
	public void start() throws Exception{
		/*
		 * 实现要求:
		 *  首先单独启动一个线程，用来运行SaveLogHandler
		 *  这个任务，目的是保存所有配对日志
		 *  然后开始循环监听服务端端口，一旦一个客户端连接了，
		 *  就实例化一个ClientHander,然后将该任务交给线程池
		 *  使其分配线程来处理与该客户端的交互。
		 *  
		 */
		try{
			System.out.println("服务端开始工作...");
			//启动用来保存日志的线程
			saveLogHandler handler = new saveLogHandler();
			Thread t = new Thread(handler);
			t.start();
			while(true){
				Socket socket = server.accept();
				clientHandler clientHandler = new clientHandler(socket);
				threadPool.execute(clientHandler);
			}
		}catch(Exception e){
			e.printStackTrace();
			throw e;
		}
		
	}
	/**
	 * 该线程负责从消息队列中取出每一条配对日志，
	 * 并存入到serverLogFile文件
	 * @author Administrator
	 *
	 */
	class saveLogHandler implements Runnable{
		/*
		 * 实现需求:
		 * 该线程任务是用来循环从消息队列(messageQueue)中
		 * 取出每一条配对日志，然后按行写入到serverLogFile
		 * 表示的文件中
		 * 该线程要一直工作，若消息队列中暂时没有新的配对日志
		 * 可使当前线程阻塞500毫秒，在阻塞等待前，应当缓冲流
		 * 中缓冲的日志全部写入文件。
		 * 
		 * 实现步骤:
		 * 1:创建PrintWriter,包装FileOutputStream时
		 *   要注意，应使用追加写模式。
		 * 2:死循环以下内容
		 *   2.1:判断消息队列中是否还有日志
		 *   2.2:若有日志，则取出一条然后通过PW写出。
		 *   2.3:若没有日志，则先将PW缓存的所有日志一次
		 *       行写出，然后阻塞线程500毫秒。      
		 */
		public void run(){
			PrintWriter pw = null;
			try{
				FileOutputStream fos = new FileOutputStream(serverLogFile);
				pw = new PrintWriter(fos,true);
				while(true){
					if(messageQueue.size()>0){
						String message = messageQueue.poll();
						pw.println(message);
					}else{
						pw.flush();
						Thread.sleep(500);
					}
				}
			}catch(Exception e){
				e.printStackTrace();
			}finally{
				pw.close();
			}
		}
	}
	/**
	 * 处理一个指定客户端请求
	 * @author Administrator
	 *
	 */
	class clientHandler implements Runnable{
		/*
		 * 实现要求:
		 * 首先接收客户端发送过来的所有配对日志，
		 * 直到读取到"OVER"为止，每读取到一条配对
		 * 日志就将其添加到消息队列中以便保存到本地的
		 * 文件(serverLogFile表示的文件)中，然后
		 * 回复客户端"OK"
		 * 执行步骤:
		 * 1:通过Socket创建输出流，用来给客户端
		 *   发送响应
		 * 2:通过Socket创建输入流，读取客户端发送
		 *   过来的日志
		 * 3:循环读取客户端发送过来的每一行字符串，并
		 *   先判断是否为字符串"OVER",若不是，则是
		 *   一条配对日志，那么将该日志存入messageQueue
		 *   消息队列中，若是，则停止读取。
		 * 4:成功读取所有日志后回复客户端"OK"   
		 * 5:若在任何一个环节出现异常，回复客户端"ERROR"
		 * 6:在finally中关闭Socket   
		 */
		private Socket socket;
		public clientHandler(Socket socket){
			this.socket = socket;
		}
		
		public void run(){
			PrintWriter pw = null;
			try{
				InputStream in = socket.getInputStream();
				BufferedReader br = new BufferedReader(new InputStreamReader(in));
				OutputStream out = socket.getOutputStream();
				pw = new PrintWriter(new OutputStreamWriter(out));
				String str = null;
//				while(!"OVER".equals(str = br.readLine())&&str!=null){
//					messageQueue.add(str);
//				}
				while((str=br.readLine())!=null){
					if("OVER".equals(str)){
						break;
					}else{
						messageQueue.add(str);
					}
				}
				System.out.println("服务端成功读取日志，向客户端发送反馈信息OK");
				pw.println("OK");
			}catch(Exception e){
				e.printStackTrace();
				pw.println("ERROR");
			}finally{
				pw.flush();
				try {
					socket.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}
	
	public static void main(String[] args) {
		try{
			DMSServer server = new DMSServer();
			server.start();
		}catch(Exception e){
			e.printStackTrace();
			System.out.println("启动服务端失败!");
		}
		
	}
}


