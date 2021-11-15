package com.team.project.KakaoMapAPI

import android.Manifest
import android.app.AlertDialog
import android.content.ContentValues.TAG
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.team.project.MainActivity
import com.team.project.R
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

import com.team.project.databinding.FragmentMapBinding
import net.daum.mf.map.api.MapPOIItem
import net.daum.mf.map.api.MapPoint
import net.daum.mf.map.api.MapView
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class MapFragment : Fragment() {

    private lateinit var binding: FragmentMapBinding

    private lateinit var mainActivity:MainActivity


    companion object {
        private val BASE_URL = "https://dapi.kakao.com/"
        const val API_KEY = "KakaoAK c4dc56e62c47c6173e7c78f71f59f279"  // REST API 키
    }

    private val ACCESS_FINE_LOCATION = 1000     // Request Code

    private val listItems = arrayListOf<MapListModel>()   // 리사이클러 뷰 아이템
    private val listAdapter = MapListAdapter(listItems)    // 리사이클러 뷰 어댑터
    private var pageNumber = 1      // 검색 페이지 번호
    private var keyword = ""        // 검색 키워드

    override fun onAttach(context: Context) {
        super.onAttach(context)
        mainActivity = getActivity() as MainActivity

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        // binding 할당
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_map, container, false)

        // 리사이클러 뷰
        binding.rvList.layoutManager = LinearLayoutManager(mainActivity, LinearLayoutManager.VERTICAL, false)
        binding.rvList.adapter = listAdapter

//         binding.mapView.currentLocationTrackingMode = MapView.CurrentLocationTrackingMode.TrackingModeOnWithoutHeading

        if (checkLocationService()) {
            // GPS가 켜져있을 경우
            permissionCheck()
        } else {
            // GPS가 꺼져있을 경우
            Toast.makeText(mainActivity, "GPS를 켜주세요", Toast.LENGTH_SHORT).show()
        }


        // 리스트 아이템 클릭 시 해당 위치로 이동
        listAdapter.setItemClickListener(object: MapListAdapter.OnItemClickListener {
            override fun onClick(v: View, position: Int) {
                val mapPoint = MapPoint.mapPointWithGeoCoord(listItems[position].y, listItems[position].x)
                binding.mapView.setMapCenterPointAndZoomLevel(mapPoint, 1, true)
            }
        })

        /*** ***/

        searchKeyword("애견",pageNumber)

        // binding.mapView.currentLocationTrackingMode = MapView.CurrentLocationTrackingMode.TrackingModeOnWithoutHeading



        Log.d(TAG,"위치:"+MapView.CurrentLocationTrackingMode.TrackingModeOnWithoutHeading)

//        // 검색 버튼
//        binding.btnSearch.setOnClickListener {
//            keyword = binding.etSearchField.text.toString()
//            pageNumber = 1
//            searchKeyword(keyword, pageNumber)
//        }

//        // 이전 페이지 버튼
//        binding.btnPrevPage.setOnClickListener {
//            pageNumber--
//            binding.tvPageNumber.text = pageNumber.toString()
//            searchKeyword(keyword, pageNumber)
//        }
//
//        // 다음 페이지 버튼
//        binding.btnNextPage.setOnClickListener {
//            pageNumber++
//            binding.tvPageNumber.text = pageNumber.toString()
//            searchKeyword(keyword, pageNumber)
//        }


        onClick(binding.btnPetHospital,"동물병원")
        onClick(binding.btnPetCafe,"애견카페")
        onClick(binding.btnPetFood,"애견식당")
        onClick(binding.btnPetHotel,"애견동반호텔")
        onClick(binding.btnPetStore,"애견용품")
        onClick(binding.btnAll,"애견")

        return binding.root
    }

    fun onClick(btn:Button,keyword: String){
        btn.setOnClickListener {
            searchKeyword(keyword,pageNumber)
        }

    }

    // 키워드 검색 함수
    private fun searchKeyword(keyword: String, page: Int) {
        val retrofit = Retrofit.Builder()          // Retrofit 구성
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        val api = retrofit.create(KakaoAPI::class.java)            // 통신 인터페이스를 객체로 생성
        val call = api.getSearchKeyword(API_KEY, keyword, page,129.07707081187226,35.20486744428367)    // 검색 조건 입력

        // API 서버에 요청
        call.enqueue(object: Callback<ResultSearchKeyword> {
            override fun onResponse(call: Call<ResultSearchKeyword>, response: Response<ResultSearchKeyword>) {
                // 통신 성공
                addItemsAndMarkers(response.body(),keyword)
                // 통신 성공 (검색 결과는 response.body()에 담겨있음)
                Log.d("Test", "Raw: ${response.raw()}")
                Log.d("Test", "Body: ${response.body()}")
            }

            override fun onFailure(call: Call<ResultSearchKeyword>, t: Throwable) {
                // 통신 실패
                Log.w("LocalSearch", "통신 실패: ${t.message}")
            }
        })
    }

    // 검색 결과 처리 함수
    private fun addItemsAndMarkers(searchResult: ResultSearchKeyword?,keyword:String) {
        if (!searchResult?.documents.isNullOrEmpty()) {
            // 검색 결과 있음
            listItems.clear()                   // 리스트 초기화
            binding.mapView.removeAllPOIItems() // 지도의 마커 모두 제거
            for (document in searchResult!!.documents) {

                var distanc = when { document.distanc == null -> ""  else -> document.distanc }


                // 결과를 리사이클러 뷰에 추가
                val item = MapListModel(document.place_name,
                    document.road_address_name,
                    distanc,
                    document.place_url,
                    document.phone,
                    document.category_name,
                    document.x.toDouble(),
                    document.y.toDouble(),
                    keyword)


                listItems.add(item)

                // 지도에 마커 추가
                // val point = MapPOIItem()

                val customMarker = MapPOIItem()

                customMarker.setItemName("Default Marker");
                customMarker.setTag(0);
                customMarker.setCustomImageResourceId(R.drawable.pethospital); // 마커 이미지.
                customMarker.setMarkerType(MapPOIItem.MarkerType.BluePin); // 기본으로 제공하는 BluePin 마커 모양.
                customMarker.setSelectedMarkerType(MapPOIItem.MarkerType.RedPin); // 마커를 클릭했을때, 기본으로 제공하는 RedPin 마커 모양.



                customMarker.apply {
                    itemName = document.place_name
                    mapPoint = MapPoint.mapPointWithGeoCoord(document.y.toDouble(),
                        document.x.toDouble())
                    markerType = MapPOIItem.MarkerType.BluePin
                    selectedMarkerType = MapPOIItem.MarkerType.RedPin
                }
                binding.mapView.addPOIItem(customMarker)
            }
            listAdapter.notifyDataSetChanged()

//            binding.btnNextPage.isEnabled = !searchResult.meta.is_end // 페이지가 더 있을 경우 다음 버튼 활성화
//            binding.btnPrevPage.isEnabled = pageNumber != 1             // 1페이지가 아닐 경우 이전 버튼 활성화

        } else {
            // 검색 결과 없음
            Toast.makeText(mainActivity, "검색 결과가 없습니다", Toast.LENGTH_SHORT).show()
        }
    }

    // 위치 권한 확인
    private fun permissionCheck() {
        val preference = mainActivity.getPreferences(MODE_PRIVATE)
        val isFirstCheck = preference.getBoolean("isFirstPermissionCheck", true)
        if (ContextCompat.checkSelfPermission(mainActivity, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // 권한이 없는 상태
            if (ActivityCompat.shouldShowRequestPermissionRationale(mainActivity, Manifest.permission.ACCESS_FINE_LOCATION)) {
                // 권한 거절 (다시 한 번 물어봄)
                val builder = AlertDialog.Builder(mainActivity)
                builder.setMessage("현재 위치를 확인하시려면 위치 권한을 허용해주세요.")
                builder.setPositiveButton("확인") { dialog, which ->
                    ActivityCompat.requestPermissions(mainActivity, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), ACCESS_FINE_LOCATION)
                }
                builder.setNegativeButton("취소") { dialog, which ->

                }
                builder.show()
            } else {
                if (isFirstCheck) {
                    // 최초 권한 요청
                    preference.edit().putBoolean("isFirstPermissionCheck", false).apply()
                    ActivityCompat.requestPermissions(mainActivity, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), ACCESS_FINE_LOCATION)
                } else {
                    // 다시 묻지 않음 클릭 (앱 정보 화면으로 이동)
                    val builder = AlertDialog.Builder(mainActivity)
                    builder.setMessage("현재 위치를 확인하시려면 설정에서 위치 권한을 허용해주세요.")
                    builder.setPositiveButton("설정으로 이동") { dialog, which ->
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:com.team.project"))
                        startActivity(intent)
                    }
                    builder.setNegativeButton("취소") { dialog, which ->

                    }
                    builder.show()
                }
            }
        } else {
            // 권한이 있는 상태
            startTracking()
        }
    }

    // 위치추적 시작
    private fun startTracking() {
        binding.mapView.currentLocationTrackingMode = MapView.CurrentLocationTrackingMode.TrackingModeOnWithoutHeading
    }

    // GPS가 켜져있는지 확인
    private fun checkLocationService(): Boolean {
        val locationManager = mainActivity.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
    }
}