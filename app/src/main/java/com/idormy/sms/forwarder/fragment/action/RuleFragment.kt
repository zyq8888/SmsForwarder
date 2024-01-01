package com.idormy.sms.forwarder.fragment.action

import android.annotation.SuppressLint
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.google.gson.Gson
import com.idormy.sms.forwarder.R
import com.idormy.sms.forwarder.adapter.RuleRecyclerAdapter
import com.idormy.sms.forwarder.adapter.base.ItemMoveCallback
import com.idormy.sms.forwarder.adapter.spinner.RuleSpinnerAdapter
import com.idormy.sms.forwarder.adapter.spinner.RuleSpinnerItem
import com.idormy.sms.forwarder.core.BaseFragment
import com.idormy.sms.forwarder.core.Core
import com.idormy.sms.forwarder.database.entity.Rule
import com.idormy.sms.forwarder.databinding.FragmentTasksActionRuleBinding
import com.idormy.sms.forwarder.entity.MsgInfo
import com.idormy.sms.forwarder.entity.TaskSetting
import com.idormy.sms.forwarder.entity.action.RuleSetting
import com.idormy.sms.forwarder.utils.KEY_BACK_DATA_ACTION
import com.idormy.sms.forwarder.utils.KEY_BACK_DESCRIPTION_ACTION
import com.idormy.sms.forwarder.utils.KEY_EVENT_DATA_ACTION
import com.idormy.sms.forwarder.utils.Log
import com.idormy.sms.forwarder.utils.TASK_ACTION_RULE
import com.idormy.sms.forwarder.utils.TaskWorker
import com.idormy.sms.forwarder.utils.XToastUtils
import com.idormy.sms.forwarder.workers.ActionWorker
import com.xuexiang.xaop.annotation.SingleClick
import com.xuexiang.xpage.annotation.Page
import com.xuexiang.xrouter.annotation.AutoWired
import com.xuexiang.xrouter.launcher.XRouter
import com.xuexiang.xui.utils.CountDownButtonHelper
import com.xuexiang.xui.widget.actionbar.TitleBar
import com.xuexiang.xutil.resource.ResUtils.getDrawable
import io.reactivex.SingleObserver
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import java.util.Date

@Page(name = "Rule")
@Suppress("PrivatePropertyName", "DEPRECATION")
class RuleFragment : BaseFragment<FragmentTasksActionRuleBinding?>(), View.OnClickListener {

    private val TAG: String = RuleFragment::class.java.simpleName
    private var titleBar: TitleBar? = null
    private var mCountDownHelper: CountDownButtonHelper? = null

    //所有转发规则下拉框
    private var ruleListAll = mutableListOf<Rule>()
    private val ruleSpinnerList = mutableListOf<RuleSpinnerItem>()
    private lateinit var ruleSpinnerAdapter: RuleSpinnerAdapter<*>

    //已选转发规则列表
    private var ruleId = 0L
    private var ruleListSelected = mutableListOf<Rule>()
    private lateinit var ruleRecyclerView: RecyclerView
    private lateinit var ruleRecyclerAdapter: RuleRecyclerAdapter

    @JvmField
    @AutoWired(name = KEY_EVENT_DATA_ACTION)
    var eventData: String? = null

    override fun initArgs() {
        XRouter.getInstance().inject(this)
    }

    override fun viewBindingInflate(
        inflater: LayoutInflater,
        container: ViewGroup,
    ): FragmentTasksActionRuleBinding {
        return FragmentTasksActionRuleBinding.inflate(inflater, container, false)
    }

    override fun initTitle(): TitleBar? {
        titleBar = super.initTitle()!!.setImmersive(false).setTitle(R.string.task_rule)
        return titleBar
    }

    /**
     * 初始化控件
     */
    override fun initViews() {
        //测试按钮增加倒计时，避免重复点击
        mCountDownHelper = CountDownButtonHelper(binding!!.btnTest, 1)
        mCountDownHelper!!.setOnCountDownListener(object : CountDownButtonHelper.OnCountDownListener {
            override fun onCountDown(time: Int) {
                binding!!.btnTest.text = String.format(getString(R.string.seconds_n), time)
            }

            override fun onFinished() {
                binding!!.btnTest.text = getString(R.string.test)
                //获取转发规则列表
                getRuleList()
            }
        })

        Log.d(TAG, "initViews eventData:$eventData")
        if (eventData != null) {
            val settingVo = Gson().fromJson(eventData, RuleSetting::class.java)
            binding!!.rgStatus.check(if (settingVo.status == 1) R.id.rb_status_enable else R.id.rb_status_disable)
            Log.d(TAG, settingVo.ruleList.toString())
            settingVo.ruleList.forEach {
                ruleId = it.id
                ruleListSelected.add(it)
            }
            Log.d(TAG, "initViews settingVo:$settingVo")
        }

        //初始化转发规则下拉框
        initRule()
    }

    @SuppressLint("SetTextI18n")
    override fun initListeners() {
        binding!!.btnTest.setOnClickListener(this)
        binding!!.btnDel.setOnClickListener(this)
        binding!!.btnSave.setOnClickListener(this)
    }

    @SingleClick
    override fun onClick(v: View) {
        try {
            when (v.id) {
                R.id.btn_test -> {
                    mCountDownHelper?.start()
                    try {
                        val settingVo = checkSetting()
                        Log.d(TAG, settingVo.toString())
                        val taskAction = TaskSetting(TASK_ACTION_RULE, getString(R.string.task_rule), settingVo.description, Gson().toJson(settingVo), requestCode)
                        val taskActionsJson = Gson().toJson(arrayListOf(taskAction))
                        val msgInfo = MsgInfo("task", getString(R.string.task_rule), settingVo.description, Date(), getString(R.string.task_rule))
                        val actionData = Data.Builder().putLong(TaskWorker.taskId, 0).putString(TaskWorker.taskActions, taskActionsJson).putString(TaskWorker.msgInfo, Gson().toJson(msgInfo)).build()
                        val actionRequest = OneTimeWorkRequestBuilder<ActionWorker>().setInputData(actionData).build()
                        WorkManager.getInstance().enqueue(actionRequest)
                    } catch (e: Exception) {
                        mCountDownHelper?.finish()
                        e.printStackTrace()
                        Log.e(TAG, "onClick error: ${e.message}")
                        XToastUtils.error(e.message.toString(), 30000)
                    }
                    return
                }

                R.id.btn_del -> {
                    popToBack()
                    return
                }

                R.id.btn_save -> {
                    val settingVo = checkSetting()
                    val intent = Intent()
                    intent.putExtra(KEY_BACK_DESCRIPTION_ACTION, settingVo.description)
                    intent.putExtra(KEY_BACK_DATA_ACTION, Gson().toJson(settingVo))
                    setFragmentResult(TASK_ACTION_RULE, intent)
                    popToBack()
                    return
                }
            }
        } catch (e: Exception) {
            XToastUtils.error(e.message.toString(), 30000)
            e.printStackTrace()
            Log.e(TAG, "onClick error: ${e.message}")
        }
    }

    //初始化转发规则
    @SuppressLint("SetTextI18n", "NotifyDataSetChanged")
    private fun initRule() {
        //初始化转发规则下拉框
        binding!!.spRule.setOnItemClickListener { _: AdapterView<*>, _: View, position: Int, _: Long ->
            try {
                val item = ruleSpinnerAdapter.getItemSource(position) as RuleSpinnerItem
                ruleId = item.id!!
                if (ruleId > 0L) {
                    ruleListSelected.forEach {
                        if (ruleId == it.id) {
                            XToastUtils.warning(getString(R.string.rule_contains_tips))
                            return@setOnItemClickListener
                        }
                    }
                    ruleListAll.forEach {
                        if (ruleId == it.id) {
                            ruleListSelected.add(it)
                        }
                    }
                    ruleRecyclerAdapter.notifyDataSetChanged()
                }
            } catch (e: Exception) {
                XToastUtils.error(e.message.toString())
            }
        }

        // 初始化已选转发规则列表 RecyclerView 和 Adapter
        ruleRecyclerView = binding!!.recyclerRules
        ruleRecyclerAdapter = RuleRecyclerAdapter(ruleListSelected, { position ->
            ruleListSelected.removeAt(position)
            ruleRecyclerAdapter.notifyItemRemoved(position)
            ruleRecyclerAdapter.notifyItemRangeChanged(position, ruleListSelected.size) // 更新索引
        })
        ruleRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = ruleRecyclerAdapter
        }
        val ruleMoveCallback = ItemMoveCallback(object : ItemMoveCallback.Listener {
            override fun onItemMove(fromPosition: Int, toPosition: Int) {
                Log.d(TAG, "onItemMove: $fromPosition $toPosition")
                ruleRecyclerAdapter.onItemMove(fromPosition, toPosition)
                ruleListSelected = ruleRecyclerAdapter.itemList
            }

            override fun onDragFinished() {
                ruleListSelected = ruleRecyclerAdapter.itemList
                //ruleRecyclerAdapter.notifyDataSetChanged()
                Log.d(TAG, "onDragFinished: $ruleListSelected")
            }
        })
        val ruleTouchHelper = ItemTouchHelper(ruleMoveCallback)
        ruleTouchHelper.attachToRecyclerView(ruleRecyclerView)
        ruleRecyclerAdapter.setTouchHelper(ruleTouchHelper)

        //获取转发规则列表
        getRuleList()
    }

    //获取转发规则列表
    private fun getRuleList() {
        Core.rule.getAll().subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe(object : SingleObserver<List<Rule>> {
            override fun onSubscribe(d: Disposable) {}

            override fun onError(e: Throwable) {
                e.printStackTrace()
                Log.e(TAG, "getRuleList error: ${e.message}")
            }

            @SuppressLint("NotifyDataSetChanged")
            override fun onSuccess(ruleList: List<Rule>) {
                if (ruleList.isEmpty()) {
                    XToastUtils.error(R.string.add_rule_first)
                    return
                }

                ruleSpinnerList.clear()
                ruleListAll = ruleList as MutableList<Rule>
                for (rule in ruleList) {
                    val name = if (rule.name.length > 20) rule.name.substring(0, 19) else rule.name
                    val icon = when (rule.type) {
                        "sms" -> R.drawable.auto_task_icon_sms
                        "call" -> R.drawable.auto_task_icon_incall
                        "app" -> R.drawable.auto_task_icon_start_activity
                        else -> R.drawable.auto_task_icon_sms
                    }
                    ruleSpinnerList.add(RuleSpinnerItem(name, getDrawable(icon), rule.id, rule.status))
                }
                ruleSpinnerAdapter = RuleSpinnerAdapter(ruleSpinnerList).setIsFilterKey(true).setFilterColor("#EF5362").setBackgroundSelector(R.drawable.selector_custom_spinner_bg)
                binding!!.spRule.setAdapter(ruleSpinnerAdapter)
                //ruleSpinnerAdapter.notifyDataSetChanged()

                //更新ruleListSelected的状态与名称
                ruleListSelected.forEach {
                    ruleListAll.forEach { rule ->
                        if (it.id == rule.id) {
                            //it.name = rule.name
                            it.status = rule.status
                        }
                    }
                }
                ruleRecyclerAdapter.notifyDataSetChanged()

            }
        })
    }

    //检查设置
    @SuppressLint("SetTextI18n")
    private fun checkSetting(): RuleSetting {
        if (ruleListSelected.isEmpty() || ruleId == 0L) {
            throw Exception(getString(R.string.new_rule_first))
        }

        val description = StringBuilder()
        val status: Int
        if (binding!!.rgStatus.checkedRadioButtonId == R.id.rb_status_enable) {
            status = 1
            description.append(getString(R.string.enable))
        } else {
            status = 0
            description.append(getString(R.string.disable))
        }
        description.append(getString(R.string.menu_rules)).append(", ").append(getString(R.string.specified_rule)).append(": ")
        description.append(ruleListSelected.joinToString(",") { it.id.toString() })

        return RuleSetting(description.toString(), status, ruleListSelected)
    }
}