package com.dms;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
/**
 * 该类是一个工具类，负责客户端的IO操作
 * @author Administrator
 *
 */
public class IOUtil {

	/**
	 * 将指定的long值以字符串的形式写入到给定文件的第一行
	 */
	public static void saveLong(long lon,File file){
		try{
			FileOutputStream fos = new FileOutputStream(file);
			OutputStreamWriter osw = new OutputStreamWriter(fos);
			PrintWriter pw = new PrintWriter(osw);
			pw.println(lon);
			pw.close();
		}catch(Exception e){
			e.printStackTrace();
		}
	}
	/**
	 * 从给定文件中读取第一行字符串，然后将其转换为一个long值后返回
	 */
	public static long readLong(File file) throws Exception{
		try{
			FileInputStream fis = new FileInputStream(file);
			InputStreamReader isr = new InputStreamReader(fis);
			BufferedReader br = new BufferedReader(isr);
			long lon = Long.parseLong(br.readLine());
			br.close();
			return lon;
		}catch(Exception e){
			e.printStackTrace();
			throw e;
		}
	}
	/**
	 * 从给定的RandomAccessFile当前位置开始连续
	 * 读取length个字节，并转换为字符串后返回
	 * 需要注意，由于业务逻辑要求，所以RandomAccessFile使用完毕后不要关闭。
	 */
	public static String readString(RandomAccessFile raf,int length) throws Exception{
		byte[] arr = new byte[length];
		raf.read(arr);
		String str = new String(arr,0,length,"ISO8859-1");
		return str;
	}
	/**
	 * 将集合中每个元素的toString方法返回的字符串以行为单位写入到指定文件中。
	 */
	public static void saveCollection(Collection c,File file){
		try {
			FileOutputStream fos = new FileOutputStream(file);
			OutputStreamWriter osw = new OutputStreamWriter(fos);
			PrintWriter pw = new PrintWriter(osw);
			for(Object o : c){
				pw.println(o);
			}
			pw.close();
			System.out.println(file+"保存成功！");
		} catch (FileNotFoundException e) {
			System.out.println(file+"保存失败！");
			e.printStackTrace();
		}
	}
	/**
	 * 从给定的文件中读取每一条配对日志，并存入一个集合中然后返回。
	 */
	public static List<LogData> loadLogData(File file) throws Exception{
		FileInputStream fis = new FileInputStream(file);
		InputStreamReader isr = new InputStreamReader(fis);
		BufferedReader br = new BufferedReader(isr);
		String str = null;
		List<LogData> list = new ArrayList<LogData>();
		while((str=br.readLine())!=null){
			LogData log = new LogData(str);
			list.add(log);
		}
		br.close();
		return list;
	}
	/**
	 * 从给定的文件中读取每一行字符串(配对日志)并存入一个集合后返回
	 */
	public static List<String> loadLogRec(File file) throws Exception{
		List<String> list = new ArrayList<String>();
		FileInputStream fis = new FileInputStream(file);
		InputStreamReader isr = new InputStreamReader(fis);
		BufferedReader br = new BufferedReader(isr);
		String str = null;
		while((str=br.readLine())!=null){
			list.add(str);
		}
		br.close();
		return list;
	}
}
