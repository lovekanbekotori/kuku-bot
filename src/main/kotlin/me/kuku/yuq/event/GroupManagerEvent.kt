package me.kuku.yuq.event

import com.IceCreamQAQ.Yu.annotation.Event
import com.IceCreamQAQ.Yu.annotation.EventListener
import com.icecreamqaq.yuq.event.GroupMessageEvent
import com.icecreamqaq.yuq.firstString
import com.icecreamqaq.yuq.message.Image
import com.icecreamqaq.yuq.mf
import com.icecreamqaq.yuq.mif
import com.icecreamqaq.yuq.yuq
import me.kuku.yuq.entity.GroupQQEntity
import me.kuku.yuq.logic.QQAILogic
import me.kuku.yuq.service.GroupQQService
import me.kuku.yuq.service.QQGroupService
import me.kuku.yuq.utils.BotUtils
import javax.inject.Inject

@EventListener
class GroupManagerEvent {
    @Inject
    private lateinit var qqGroupService: QQGroupService
    @Inject
    private lateinit var groupQQService: GroupQQService
    @Inject
    private lateinit var qqAiLogic: QQAILogic

    @Event(weight = Event.Weight.high)
    fun switchGroup(e: GroupMessageEvent){
        val qqGroupEntity = qqGroupService.findByGroup(e.message.group!!)
        val body = e.message.body
        if (body.size < 1) return
        val msg = body[0].toPath()
        if (!msg.startsWith("机器人")) {
            if (qqGroupEntity?.status != true) {
                e.cancel = true
            }
        }
    }

    @Event
    fun keyword(e: GroupMessageEvent){
        val group = e.message.group!!
        if (yuq.groups[group]?.bot?.isAdmin() != true) return
        val qq = try {
            e.message.qq!!
        }catch (e: Exception){
            BotUtils.regex("[0-9]*", e.message!!)?.toLong() ?: return
        }
        if (yuq.groups[group]?.get(qq)?.isAdmin() == true) return
        val qqGroupEntity = qqGroupService.findByGroup(group) ?: return
        val keywordJsonArray = qqGroupEntity.getKeywordJsonArray()
        for (i in keywordJsonArray.indices){
            val keyword = keywordJsonArray.getString(i)
            if (keyword in e.message.sourceMessage.toString()){
                e.message.recall()
                val violation = this.violation(qq, group)
                if (violation >= 5) return
                yuq.groups[group]?.members?.get(qq)?.ban(10 * 60)
                yuq.sendMessage(mf.newGroup(group).plus(mif.at(e.message.qq!!)).plus(
                        "检测到违规词\"$keyword\"，您已被禁言。\n您当前的违规次数为${violation}次。\n累计违规5次会被踢出本群哦！！"))
                return
            }
        }
    }

    @Event
    fun qa(e: GroupMessageEvent){
        val qqGroupEntity = qqGroupService.findByGroup(e.message.group!!) ?: return
        val message = e.message
        if (message.toPath().isEmpty()) return
        if (message.toPath()[0] == "删问答") return
        val qaJsonArray = qqGroupEntity.getQaJsonArray()
        for (i in qaJsonArray.indices){
            val jsonObject = qaJsonArray.getJSONObject(i)
            if (jsonObject.getString("q") in message.firstString()){
                yuq.sendMessage(mf.newGroup(message.group!!).plus(jsonObject.getString("a")))
                return
            }
        }
    }

    @Event
    fun pic(e: GroupMessageEvent){
        val group = e.message.group!!
        val qq = e.message.qq!!
        if (yuq.groups[group]?.bot?.isAdmin() != true) return
        val qqGroupEntity = qqGroupService.findByGroup(group) ?: return
        if (qqGroupEntity.pic == true){
            val bodyList = e.message.body
            for (body in bodyList){
                if (body is Image){
                    val url = body.url
                    val b = qqAiLogic.pornIdentification(url)
                    if (b){
                        e.message.recall()
                        val violation = this.violation(qq, group)
                        if (violation >= 5) return
                        yuq.groups[group]?.members?.get(qq)?.ban(10 * 60)
                        yuq.sendMessage(mf.newGroup(group).plus(mif.at(e.message.qq!!)).plus(
                                "检测到色情图片，您已被禁言\n您当前的违规次数为${violation}次。\n累计违规5次会被踢出本群哦！！"))
                    }
                }
            }
        }
    }

    private fun violation(qq: Long, group: Long): Int{
        val groupQQEntity = groupQQService.findByQQAndGroup(qq, group) ?: GroupQQEntity(null, qq, group)
        val violationCount = groupQQEntity.violationCount
        if (violationCount == 4) {
            yuq.groups[group]?.members?.get(qq)?.kick()
            yuq.sendMessage(mf.newGroup(group).plus("${qq}违禁次数已达上限，被移走了！！"))
        }else {
            groupQQEntity.violationCount = violationCount + 1
            groupQQService.save(groupQQEntity)
        }
        return violationCount + 1
    }

}
