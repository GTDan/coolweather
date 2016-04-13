package com.coolweather.app.util;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class HttpUtil {
	
	public static void sendHttpRequest(final String address, 
			final HttpCallbackListener listener) {
		new Thread(new Runnable() {

			@Override
			public void run() {
				// TODO Auto-generated method stub
				HttpURLConnection connection = null;
				try {
					URL url = new URL(address);
					connection = (HttpURLConnection)url.openConnection();
					
					// "GET":希望从服务器哪里获取数据
					// "POST":希望提交数据给服务器
					connection.setRequestMethod("GET");
					// 设置连接超时毫秒数
					connection.setConnectTimeout(8000);
					// 设置读取超时毫秒数
					connection.setReadTimeout(8000);
					// 获取服务返回的输入流
					InputStream in = connection.getInputStream();
					BufferedReader reader = new BufferedReader(new InputStreamReader(in));
					StringBuilder response = new StringBuilder();
					String line;
					while( (line = reader.readLine()) != null ) {
						response.append(line);
					}
					if(listener != null) {
						//回调onFinish()方法
						listener.onFinish(response.toString());
					}
				}catch(Exception e) {
					if(listener != null) {
						//回调Error()方法
						listener.onError(e);
					}
				}finally {
					if( connection != null ) {
						// 将HTTP连接关掉！
						connection.disconnect();
					}
				}
			}
			
		}).start();
	}
	
	
}
