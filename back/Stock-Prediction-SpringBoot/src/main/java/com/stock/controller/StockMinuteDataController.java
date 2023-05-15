package com.stock.controller;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.stock.annotation.RoleEnum;
import com.stock.annotation.Roles;
import com.stock.entity.*;
import com.stock.service.IStockMinuteDataService;
import com.stock.service.IStockMinuteNameService;
import com.stock.util.GetCurrentTime;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.stock.controller.StockDataController.HTTP_CLIENT;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author admin
 * @since 2023-05-09
 */
@RestController
@RequestMapping("/stockMinuteData")
@Api("股票分钟级数据接口")
public class StockMinuteDataController {
    @Autowired
    IStockMinuteNameService stockMinuteNameService;
    @Autowired
    IStockMinuteDataService stockMinuteDataService;
    @Autowired
    GetCurrentTime getCurrentTime;

    @Value("${stock.data.dir}")
    String dataDir;

    @Roles(RoleEnum.Admin)
    @ApiOperation("添加分钟级股票数据，同时会自动添加股票名称信息")
    @PostMapping("/add")
    public Result add(MultipartFile file) throws Exception {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.endsWith(".txt")) {
            return Result.error(null, "文件需要为txt文件");
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), "gbk"));
        BufferedReader reader2 = new BufferedReader(new InputStreamReader(file.getInputStream(), "gbk"));
        StringBuilder builder = new StringBuilder();
        String readLine;
        builder.append(reader2.readLine()).append("\n");
        builder.append(reader2.readLine()).append("\n");
        while ((readLine = reader2.readLine()) != null && !"".equals(readLine)) {
            String[] splits = readLine.split("\t");
            if (splits.length > 6) {
                if (splits[1].endsWith("30") || splits[1].endsWith("00")) {
                    builder.append(readLine).append("\n");
                }
            }
        }

        String firstLine = reader.readLine();

        QueryWrapper<StockMinuteName> wrapper = new QueryWrapper<>();
        String stockId = firstLine.substring(0, 6);
        wrapper.eq("stock_id", stockId);
        StockMinuteName stockMinuteName = stockMinuteNameService.getOne(wrapper);
        String name = "";

        if (stockMinuteName == null) {
            stockMinuteName = new StockMinuteName();
            stockMinuteName.setStockId(stockId);
            name = firstLine.substring(7, 12);
            if (name.endsWith("日")) {
                name = name.replace("日", "");
            }
            name = name.replace(" ", "") + "__Minute";
            stockMinuteName.setStockName(name);
            stockMinuteName.setCreateTime(getCurrentTime.getCurrentTimeByDay());
            stockMinuteNameService.save(stockMinuteName);
        }

        // 调用flask接口，即python脚本，保存文件
        MediaType mediaType = MediaType.parse("application/json");
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("stockName", name);
        jsonObject.put("dataDir", dataDir);
        jsonObject.put("text", builder.toString());
        RequestBody body = RequestBody.Companion.create(jsonObject.toJSONString(), mediaType);
        Request request = new Request.Builder()
                .url("http://127.0.0.1:5000/addStockMinute")
                .method("POST", body)
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json")
                .build();
        try (Response response = HTTP_CLIENT.newCall(request).execute()) {
            JSONObject map = JSONObject.parseObject(Objects.requireNonNull(response.body()).string());
            if (map.getInteger("code") != 200) {
                return Result.error(null, "文件保存失败");
            }
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error(null, "接口服务调用出错");
        }

        QueryWrapper<StockMinuteData> stockDataQueryWrapper = new QueryWrapper<>();
        stockDataQueryWrapper.eq("stock_id", stockId);
        stockMinuteDataService.remove(stockDataQueryWrapper);

        // 去除第二行
        reader.readLine();
        String line = reader.readLine();
        String[] splits = line.split("\t");

        ArrayList<StockMinuteData> stockDataList = new ArrayList<>();
        while (splits.length > 6) {
            StockMinuteData stockMinuteData = new StockMinuteData();
            stockMinuteData.setStockId(stockMinuteName.getStockId());
            stockMinuteData.setStockDate(splits[0]);
            stockMinuteData.setMinute(splits[1]);
            stockMinuteData.setStartPrice(Double.valueOf(splits[2]));
            stockMinuteData.setHighPrice(Double.valueOf(splits[3]));
            stockMinuteData.setLowPrice(Double.valueOf(splits[4]));
            stockMinuteData.setEndPrice(Double.valueOf(splits[5]));
            stockMinuteData.setDealNumber(Long.valueOf(splits[6]));
            stockMinuteData.setDealMoney(Double.valueOf(splits[7]));
            stockMinuteData.setCreateTime(getCurrentTime.getCurrentTimeByDay());
            stockDataList.add(stockMinuteData);

            line = reader.readLine();
            splits = line.split("\t");
        }
        stockMinuteDataService.saveBatch(stockDataList);

        return Result.success(null, "股票数据添加成功");
    }

    @ApiOperation("获取股票基本数据")
    @PostMapping("/getBasicData")
    public Result getBasicData(@org.springframework.web.bind.annotation.RequestBody Map<String, String> map) {
        String stockId = map.get("stockId");
        String startDate = map.get("startDate");
        String endDate = map.get("endDate");
        QueryWrapper<StockMinuteData> wrapper = new QueryWrapper<>();
        wrapper.eq("stock_id", stockId).between("stock_date", startDate, endDate);
        List<StockMinuteData> stockMinuteDataList = stockMinuteDataService.list(wrapper);
        for (StockMinuteData stockMinuteData : stockMinuteDataList) {
            stockMinuteData.setStockDate(stockMinuteData.getStockDate() + ":" + stockMinuteData.getMinute());
        }
        return Result.success(stockMinuteDataList, "股票基本数据获取成功");
    }

    @ApiOperation("获取股票均线数据")
    @PostMapping("/getAverageData")
    public Result getAverageData(@org.springframework.web.bind.annotation.RequestBody Map<String, String> map) {
        String stockName = map.get("stockName");
        String startDate = map.get("startDate");
        String endDate = map.get("endDate");

        // 调用flask接口，即python脚本，获取均线数据
        MediaType mediaType = MediaType.parse("application/json");
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("stockName", stockName);
        jsonObject.put("dataDir", dataDir);
        jsonObject.put("startDate", startDate);
        jsonObject.put("endDate", endDate);
        RequestBody body = RequestBody.Companion.create(jsonObject.toJSONString(), mediaType);
        Request request = new Request.Builder()
                .url("http://127.0.0.1:5000/generateMinuteMAData")
                .method("POST", body)
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json")
                .build();
        try (Response response = HTTP_CLIENT.newCall(request).execute()) {
            JSONObject jsonObjectRes = JSONObject.parseObject(Objects.requireNonNull(response.body()).string());
            if (jsonObjectRes.getInteger("code") != 200) {
                return Result.error(null, "股票均线数据获取失败");
            }

            ArrayList<AverageData> averageDataList = new ArrayList<>();
            JSONArray jsonArray = jsonObjectRes.getJSONArray("data");
            for (Object o : jsonArray) {
                JSONArray tempArr = (JSONArray) o;
                AverageData averageData = new AverageData();
                averageData.setStockDate(tempArr.get(0) + ":" + tempArr.get(8));
                averageData.setM5(Double.valueOf(String.valueOf(tempArr.get(1))));
                averageData.setM10(Double.valueOf(String.valueOf(tempArr.get(2))));
                averageData.setM20(Double.valueOf(String.valueOf(tempArr.get(3))));
                averageData.setM30(Double.valueOf(String.valueOf(tempArr.get(4))));
                averageData.setM60(Double.valueOf(String.valueOf(tempArr.get(5))));
                averageData.setM120(Double.valueOf(String.valueOf(tempArr.get(6))));
                averageData.setM250(Double.valueOf(String.valueOf(tempArr.get(7))));
                averageDataList.add(averageData);
            }
            return Result.success(averageDataList, "股票均线数据获取成功");
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error(null, "接口服务调用出错");
        }
    }
}
