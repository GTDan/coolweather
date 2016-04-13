package com.coolweather.app.activity;

import java.util.ArrayList;
import java.util.List;


import com.coolweather.app.R;
import com.coolweather.app.model.City;
import com.coolweather.app.model.CoolWeatherDB;
import com.coolweather.app.model.County;
import com.coolweather.app.model.Province;
import com.coolweather.app.util.HttpCallbackListener;
import com.coolweather.app.util.HttpUtil;
import com.coolweather.app.util.Utility;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

public class ChooseAreaActivity extends Activity{
	public static final int LEVEL_PROVINCE = 0;
	public static final int LEVEL_CITY = 1;
	public static final int LEVEL_COUNTY = 2;
	
	private ProgressDialog progressDialog;
	private TextView titleText;
	private ListView listView;
	private ArrayAdapter<String> adapter;
	private CoolWeatherDB coolWeatherDB;
	
	private List<String> dataList = new ArrayList<String>();
	/**
	 * 省列表
	 */
	private List<Province> provinceList;
	/**
	 * 市列表
	 */
	private List<City> cityList;
	/**
	 * 县列表
	 */
	private List<County> countyList;
	/**
	 * 选中的省份
	 */
	private Province selectedProvince;
	/**
	 * 选中的城市
	 * 
	 */
	private City selectedCity;
	/**
	 * 当前选中的级别
	 */
	private int currentLevel;
	
	/**
	 *  是否从WeatherActivity中跳转过来
	 */
	private boolean isFromWeatherActivity;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		isFromWeatherActivity = getIntent().getBooleanExtra("from_weather_activity", false);
		
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		
		//已经选择了城市且不是从WeatherActivity跳转过来，才会直接跳转到WeatherAcitvity
		if(prefs.getBoolean("city_selected", false) && !isFromWeatherActivity) {
			Intent intent = new Intent(this, WeatherActivity.class);
			startActivity(intent);
			finish();
			return;
		}
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.choose_area); //注意：import com.coolweather.app.R; ！！！
		listView = (ListView)findViewById(R.id.list_view);
		titleText = (TextView)findViewById(R.id.title_text);
		adapter = new ArrayAdapter<String>(this, android.R.layout.simple_expandable_list_item_1, dataList);
		listView.setAdapter(adapter);
		coolWeatherDB = CoolWeatherDB.getInstance(this);
		
		//响应列表项点击消息
		listView.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> arg0, View view, int index,
					long arg3) {
				// TODO Auto-generated method stub
				//当前列表如果在省级
				if(currentLevel == LEVEL_PROVINCE) {
					selectedProvince = provinceList.get(index);
					queryCities();
				//当前列表如果在市级
				}else if(currentLevel == LEVEL_CITY) {
					selectedCity = cityList.get(index);
					queryCounties();
				}else if(currentLevel == LEVEL_COUNTY) {
					String countyCode = countyList.get(index).getCountyCode();
					Intent intent = new Intent(ChooseAreaActivity.this, WeatherActivity.class);
					intent.putExtra("county_code", countyCode);
					startActivity(intent);
					finish();
				}
			}			
		});
		queryProvinces();
	}
	
	/**
	 * 查询全国所有的省份，优先从数据库查询，如果没有查询到再去服务器查询
	 */
	private void queryProvinces() {
		//从数据库中读取省级数据
		provinceList = coolWeatherDB.loadProvinces();
		if( provinceList.size() > 0 )
		{
			dataList.clear();
			for (Province province : provinceList) {
				dataList.add(province.getProvinceName());
			}
			adapter.notifyDataSetChanged();
			listView.setSelection(0);
			titleText.setText("中国");
			currentLevel = LEVEL_PROVINCE;
		//else：数据库中没读到数据，只能从服务器上查询数据
		} else {
			queryFromServer(null, "province");
		}
	}
	
	/**
	 * 查询选中省内所有的市，优先从数据库查询，如果没有查询到再去服务器上查询
	 */
	private void queryCities() {
		// TODO Auto-generated method stub
		cityList = coolWeatherDB.loadCities(selectedProvince.getId());
		if(cityList.size() > 0) {
			dataList.clear();
			for(City city : cityList) {
				dataList.add(city.getCityName());
			}
			adapter.notifyDataSetChanged();
			listView.setSelection(0);
			titleText.setText(selectedProvince.getProvinceName());
			currentLevel = LEVEL_CITY;
		} else {
			queryFromServer(selectedProvince.getProvinceCode(), "city");
		}
	}
	
	/**
	 * 查询选中市内所有的县，优先从数据库查询，如果没有查询到再上服务器查询
	 */
	private void queryCounties() {
		countyList = coolWeatherDB.loadCounties(selectedCity.getId());
		if(countyList.size() > 0) {
			dataList.clear();
			for(County county : countyList) {
				dataList.add(county.getCountyName());
			}
			adapter.notifyDataSetChanged();
			listView.setSelection(0);
			titleText.setText(selectedCity.getCityName());
			currentLevel = LEVEL_COUNTY;
		} else {
			queryFromServer(selectedCity.getCityCode(), "county");
		}
	}
	
	/**
	 * 根据传人的代号和类型从服务器上查询省市县数据
	 */
	private void queryFromServer(final String code, final String type) {
		String address;
		if(!TextUtils.isEmpty(code)) {
			address = "http://www.weather.com.cn/data/list3/city" + code + ".xml";
		} else {
			address = "http://www.weather.com.cn/data/list3/city.xml";
		}
		//显示进度对话框
		showProgressDialog();
		
		/**
		 * HttpCallbackListener:回调监听，需实现onFinish,onError两个接口
		 */
		//Log.v("wdy", "queryFromServer-----------address = " + address);
		HttpUtil.sendHttpRequest(address, new HttpCallbackListener() {
			
			@Override
			public void onFinish(String response) {
				// TODO Auto-generated method stub
				boolean result = false;
				if("province".equals(type)) {
					result = Utility.handleProvincesResponse(coolWeatherDB, response);
				} else if("city".equals(type)) {
					result = Utility.handleCitiesResponse(coolWeatherDB, response, selectedProvince.getId());
				} else if("county".equals(type)) {
					result = Utility.handleCountiesResponse(coolWeatherDB, response, selectedCity.getId());
				}
				
				if(result) {
					//通过runOnUiThread()方法回到主线程处理逻辑
					//queryProvinces()中涉及UI操作，需切回主线程操作
					runOnUiThread(new Runnable() {

						@Override
						public void run() {
							// TODO Auto-generated method stub
							//关闭进度提示框
							closeProgressDialog();
							if("province".equals(type)) {
								queryProvinces();
							} else if("city".equals(type)) {
								queryCities();
							} else if("county".equals(type)) {
								queryCounties();
							} 
						}
						
					});
				}
			}
			
			@Override
			public void onError(Exception e) {
				// TODO Auto-generated method stub
				//通过runOnUIThread()方法回到主线程处理逻辑
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						//关闭进度对话框
						closeProgressDialog();
						Toast.makeText(ChooseAreaActivity.this, "加载失败", Toast.LENGTH_SHORT).show();
					}
				});
			}
		});
	}
	
	/**
	 * 显示进度对话框
	 */
	private void showProgressDialog() {
		if(progressDialog == null) {
			progressDialog = new ProgressDialog(this);
			progressDialog.setMessage("正在玩命加载...");
			progressDialog.setCanceledOnTouchOutside(false);
		}
		progressDialog.show();
	}
	
	/**
	 * 关闭对话框
	 */
	private void closeProgressDialog() {
		if(progressDialog != null) {
			progressDialog.dismiss();
		}
	}
	
	/**
	 * 捕获Back按键，根据当前级别判断，应返回市列表，省列表，还是退出
	 */
	@Override
	public void onBackPressed() {
		if(currentLevel == LEVEL_COUNTY) {
			queryCities();
		} else if(currentLevel == LEVEL_CITY) {
			queryProvinces();
		} else {
			if( isFromWeatherActivity ) {
				Intent intent = new Intent(this, WeatherActivity.class);
				startActivity(intent);
			}
			finish();
		}
	}
}
