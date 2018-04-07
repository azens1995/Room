package com.cdrussell.casterio.room

import android.annotation.SuppressLint
import android.arch.lifecycle.Observer
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.support.annotation.AnyThread
import android.support.v7.app.AppCompatActivity
import android.view.*
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.TextView
import com.cdrussell.casterio.room.users.User
import com.cdrussell.casterio.room.users.UserDao
import kotlinx.android.synthetic.main.activity_task_details.*
import kotlinx.android.synthetic.main.content_task_details.*
import kotlin.concurrent.thread
import kotlinx.android.synthetic.main.content_task_details.taskId as taskIdInput

class TaskDetailsActivity : AppCompatActivity() {

    private lateinit var taskDao: TaskDao
    private lateinit var userDao: UserDao

    private lateinit var assigneeArrayAdapter: ArrayAdapter<User>

    private var task: Task? = null

    private var spinnerInitialised = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_task_details)
        setSupportActionBar(toolbar)
        configureAssigneeAdapter()

        taskDao = AppDatabase.getInstance(this).taskDao()
        userDao = AppDatabase.getInstance(this).userDao()

        val taskId = extractTaskId()
        taskDao.getTask(taskId).observe(this, Observer<Task> {
            if (it == null) {
                finish()
                return@Observer
            }

            taskTitle.text = it.title
            taskIdInput.text = it.id.toString()
            assigneeUserId.text = it.userId?.toString() ?: getString(R.string.unassigned)

            taskCompletionCheckbox.isChecked = it.completed

            task = it
        })

        userDao.getAll().observe(this, Observer<List<User>> {
            assigneeArrayAdapter.addAll(it)
            assigneeArrayAdapter.add(placeholderUserUnassign)

            assignee.isEnabled = true
        })

        taskCompletionCheckbox.setOnCheckedChangeListener { _, isChecked ->
            task?.let {
                it.completed = isChecked
                updateTask(it)
            }
        }
    }

    private fun configureAssigneeAdapter() {
        assigneeArrayAdapter = AssigneeAdapter(this, android.R.layout.simple_spinner_item)
        assigneeArrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        assigneeArrayAdapter.add(placeholderUserInstruction)
        assignee.adapter = assigneeArrayAdapter
        assignee.isEnabled = false

        assignee.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {
            }

            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                // ignore the first event - UI initialising causes this and not a user selection
                if (!spinnerInitialised) {
                    spinnerInitialised = true
                    return
                }

                val selectedUser = assigneeArrayAdapter.getItem(position)
                if (selectedUser == null || selectedUser == placeholderUserInstruction) {
                    return
                } else if (selectedUser == placeholderUserUnassign) {
                    unassignUserFromTask()
                } else {
                    assignUserToTask(selectedUser)
                }

                // reset to show "assign task" instruction
                assignee.setSelection(0, true)
            }
        }
    }

    private fun assignUserToTask(selectedUser: User) {
        task?.let {
            it.userId = selectedUser.id
            updateTask(it)
        }
    }

    private fun unassignUserFromTask() {
        task?.let {
            it.userId = null
            updateTask(it)
        }
    }

    @AnyThread
    private fun updateTask(task: Task) = thread { taskDao.update(task) }

    private fun extractTaskId(): Int {
        if (!intent.hasExtra(TASK_ID)) {
            throw IllegalArgumentException("$TASK_ID must be provided to start this Activity")
        }
        return intent.getIntExtra(TASK_ID, Int.MIN_VALUE)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_task_details, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_item_delete_task -> {

                task?.let {
                    thread { taskDao.delete(it) }
                }

                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    companion object {

        private const val TASK_ID = "INTENT_TASK_ID"

        private val placeholderUserUnassign = User(-1, "Unassign")
        private val placeholderUserInstruction = User(-2, "Assign Task")

        fun launchIntent(context: Context, taskId: Int): Intent {
            val intent = Intent(context, TaskDetailsActivity::class.java)
            intent.putExtra(TASK_ID, taskId)
            return intent
        }
    }

    private class AssigneeAdapter(context: Context, resource: Int) :
        ArrayAdapter<User>(context, resource) {

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            return configureListView(position, convertView, parent)
        }

        override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup?): View {
            return configureListView(position, convertView, parent)
        }

        @SuppressLint("SetTextI18n")
        private fun configureListView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val listItemView: View = convertView ?: LayoutInflater.from(context).inflate(
                android.R.layout.simple_spinner_dropdown_item,
                parent,
                false
            )
            val user = getItem(position)

            val textView = listItemView.findViewById<View>(android.R.id.text1) as TextView
            textView.text = user.name + if (user.id >= 0) " (id = ${user.id})" else ""
            return listItemView
        }
    }
}
