package com.stock.controller;

import cn.hutool.json.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stock.annotation.RoleEnum;
import com.stock.annotation.Roles;
import com.stock.config.WebConfig;
import com.stock.entity.Result;
import com.stock.entity.StockData;
import com.stock.entity.StockName;
import com.stock.service.IStockDataService;
import com.stock.service.IStockNameService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import java.net.URLEncoder;
import java.util.List;
import java.util.Map;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author admin
 * @since 2023-04-11
 */
@RestController
@RequestMapping("/stockName")
@Api("股票名称接口")
public class StockNameController {
    private static final String STOCK_NOT_EXIST = "{\"code\": 500,\"msg\": \"The Stock Not Exist.\"}";

    @Autowired
    IStockNameService stockNameService;
    @Autowired
    IStockDataService stockDataService;

    @Roles(RoleEnum.Admin)
    @ApiOperation("获取股票列表，分页显示")
    @PostMapping("/list")
    public Result list(@RequestBody Map<String, String> map) {
        int pageNum = Integer.parseInt(map.get("pageNum"));
        String searchStockName = map.get("searchStockName");
        String searchTime = map.get("searchTime");

        QueryWrapper<StockName> wrapper = new QueryWrapper<>();
        wrapper.like("stock_name", searchStockName).like("create_time", searchTime);

        Page<StockName> page = new Page<>(pageNum, WebConfig.PAGE_SIZE);
        Page<StockName> stockNamePage = stockNameService.page(page, wrapper);
        List<StockName> stockNameList = stockNamePage.getRecords();
        long total = stockNamePage.getTotal();
        JSONObject jsonObject = new JSONObject();
        jsonObject.putOpt("stockNameList", stockNameList);
        jsonObject.putOpt("total", total);

        return Result.success(jsonObject, "股票列表获取成功");
    }

    @ApiOperation("获取股票列表")
    @GetMapping("/listAll")
    public Result listAll() {
        return Result.success(stockNameService.list(), "股票列表获取成功");
    }

    @Roles(RoleEnum.Admin)
    @ApiOperation("导出股票数据")
    @GetMapping("/exportData")
    public void exportData(HttpServletResponse response, @RequestParam String stockId) throws Exception {
        QueryWrapper<StockName> wrapper = new QueryWrapper<>();
        wrapper.eq("stock_id", stockId);
        StockName stockName = stockNameService.getOne(wrapper);
        if (stockName == null) {
            response.getOutputStream().print(STOCK_NOT_EXIST);
            return;
        }

        QueryWrapper<StockData> stockDataQueryWrapper = new QueryWrapper<>();
        stockDataQueryWrapper.eq("stock_id", stockId);
        List<StockData> stockDataList = stockDataService.list(stockDataQueryWrapper);

        StringBuilder builder = new StringBuilder();
        builder.append(stockName.getStockId()).append("\t").append(stockName.getStockName());
        builder.append("\t").append("日线").append("\t").append("不复权").append("\n");
        builder.append("日期").append("\t").append("开盘").append("\t").append("最高").append("\t").append("最低").append("\t").append("收盘")
                .append("\t").append("成交量").append("\t").append("成交额").append("\n");
        for (StockData stockData : stockDataList) {
            builder.append(stockData.getStockDate()).append("\t");
            builder.append(stockData.getStartPrice()).append("\t");
            builder.append(stockData.getHighPrice()).append("\t");
            builder.append(stockData.getLowPrice()).append("\t");
            builder.append(stockData.getEndPrice()).append("\t");
            builder.append(stockData.getDealNumber()).append("\t");
            builder.append(stockData.getDealMoney()).append("\n");
        }
        builder.append("数据来源:通达信").append("\n");

        response.setContentType("application/octet-stream");
        response.addHeader("Content-Disposition", "attachment;filename=" + URLEncoder.encode(stockName.getStockName(), "gbk"));
        ServletOutputStream outputStream = response.getOutputStream();
        outputStream.write(builder.toString().getBytes());
        outputStream.flush();
    }

    @Roles(RoleEnum.Admin)
    @ApiOperation("删除股票数据")
    @DeleteMapping("/deleteStock")
    public Result deleteStock(@RequestParam String stockId) {
        QueryWrapper<StockName> wrapper = new QueryWrapper<>();
        wrapper.eq("stock_id", stockId);
        StockName stockName = stockNameService.getOne(wrapper);
        if (stockName == null) {
            return Result.error(null, "该股票不存在");
        }
        stockNameService.remove(wrapper);
        return Result.success(null, "股票删除成功");
    }
}
