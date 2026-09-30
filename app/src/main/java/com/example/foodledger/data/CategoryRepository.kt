package com.example.foodledger.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class CategoryRepository(context: Context) {
    private val prefs = context.getSharedPreferences("categories", Context.MODE_PRIVATE)
    fun load(): Map<String, List<String>> = runCatching {
        val raw = prefs.getString("data", null) ?: return DEFAULTS
        val json = JSONObject(raw)
        json.keys().asSequence().associateWith { key -> val a=json.getJSONArray(key); List(a.length()){a.getString(it)} }
    }.getOrDefault(DEFAULTS)
    fun save(value: Map<String,List<String>>) { val j=JSONObject(); value.forEach{(k,v)->j.put(k,JSONArray(v))}; prefs.edit().putString("data",j.toString()).apply() }
    companion object { val DEFAULTS=linkedMapOf(
        "餐饮" to listOf("早餐","午餐","晚餐","外卖","聚餐","咖啡茶饮","水果","零食"),
        "购物" to listOf("服装","鞋包","日用品","家电","美妆护肤","母婴","礼物"),
        "电子数码" to listOf("手机","平板","电脑","相机","耳机音响","智能穿戴","配件","软件服务"),
        "交通通信" to listOf("公交地铁","打车","火车","机票","加油","停车","维修保养","话费","宽带"),
        "居住" to listOf("房租","房贷","水费","电费","燃气","物业","装修","家具家居"),
        "医疗健康" to listOf("门诊","药品","住院","牙科","体检","健身","保健"),
        "教育文化" to listOf("学费","课程","书籍","文具","考试","展览演出"),
        "娱乐旅行" to listOf("电影","游戏","会员订阅","景点","酒店","旅行团","运动户外"),
        "人情家庭" to listOf("红包","礼金","孝敬父母","孩子","宠物","公益捐赠"),
        "金融" to listOf("保险","税费","手续费","利息","还款","投资"),
        "收入" to listOf("工资","奖金","兼职","经营","理财收益","退款","报销","红包","其他收入"),
        "其他" to listOf("其他消费")) }
}
