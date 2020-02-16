package org.pragyan.dalal18.fragment

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import dalalstreet.api.DalalActionServiceGrpc
import dalalstreet.api.DalalStreamServiceGrpc
import dalalstreet.api.actions.CancelOrderRequest
import dalalstreet.api.actions.CancelOrderResponse
import dalalstreet.api.actions.GetMyOpenOrdersRequest
import dalalstreet.api.actions.GetMyOpenOrdersResponse
import dalalstreet.api.datastreams.*
import io.grpc.stub.StreamObserver
import kotlinx.android.synthetic.main.fragment_my_orders.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.longToast
import org.jetbrains.anko.toast
import org.jetbrains.anko.uiThread
import org.pragyan.dalal18.R
import org.pragyan.dalal18.adapter.OrdersRecyclerAdapter
import org.pragyan.dalal18.dagger.ContextModule
import org.pragyan.dalal18.dagger.DaggerDalalStreetApplicationComponent
import org.pragyan.dalal18.data.DalalViewModel
import org.pragyan.dalal18.data.Order
import org.pragyan.dalal18.utils.ConnectionUtils
import org.pragyan.dalal18.utils.Constants
import org.pragyan.dalal18.utils.OrderItemTouchHelper
import javax.inject.Inject

class OrdersFragment : Fragment(), SwipeRefreshLayout.OnRefreshListener, OrderItemTouchHelper.BusItemTouchHelperListener {

    @Inject
    lateinit var actionServiceBlockingStub: DalalActionServiceGrpc.DalalActionServiceBlockingStub

    @Inject
    lateinit var streamServiceStub: DalalStreamServiceGrpc.DalalStreamServiceStub

    @Inject
    lateinit var streamServiceBlockingStub: DalalStreamServiceGrpc.DalalStreamServiceBlockingStub

    private lateinit var model: DalalViewModel

    private var ordersRecyclerAdapter: OrdersRecyclerAdapter? = null
    private var orderSubscriptionId: SubscriptionId? = null

    private var networkDownHandler: ConnectionUtils.OnNetworkDownHandler? = null
    private lateinit var loadingOrdersDialog: AlertDialog

    override fun onAttach(context: Context) {
        super.onAttach(context)
        try {
            networkDownHandler = context as ConnectionUtils.OnNetworkDownHandler
        } catch (classCastException: ClassCastException) {
            throw ClassCastException("$context must implement network down handler.")
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {

        val rootView = inflater.inflate(R.layout.fragment_my_orders, container, false)

        DaggerDalalStreetApplicationComponent.builder().contextModule(ContextModule(context!!)).build().inject(this)

        model = activity?.run { ViewModelProvider(this).get(DalalViewModel::class.java) }
                ?: throw Exception("Invalid activity")

        return rootView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as AppCompatActivity).supportActionBar?.title = resources.getString(R.string.orders_frag_label)
        ordersRecyclerAdapter = OrdersRecyclerAdapter(context, null)
        ordersRecycler_swipeRefreshLayout.setOnRefreshListener(this)

        with(orders_recyclerView) {
            setHasFixedSize(false)
            adapter = ordersRecyclerAdapter
            layoutManager = LinearLayoutManager(context)

            val itemTouchHelper =
                    ItemTouchHelper(OrderItemTouchHelper(0, ItemTouchHelper.LEFT, this@OrdersFragment))
            itemTouchHelper.attachToRecyclerView(this)
        }

        val dialogView = LayoutInflater.from(context).inflate(R.layout.progress_dialog, null)
        (dialogView.findViewById<View>(R.id.progressDialog_textView) as TextView).setText(R.string.updating_your_orders)
        loadingOrdersDialog = AlertDialog.Builder(context!!).setView(dialogView).setCancelable(false).create()

        getOpenOrdersAsynchronously()

        tradeFloatingActionButton.setOnClickListener {
            findNavController().navigate(R.id.trade_dest)
        }
    }

    private fun getMyOrdersSubscriptionId() {
        doAsync {
            val response = streamServiceBlockingStub.subscribe(
                    SubscribeRequest.newBuilder().setDataStreamType(DataStreamType.MY_ORDERS).setDataStreamId("").build())
            orderSubscriptionId = response.subscriptionId
            uiThread { subscribeToMyOrdersStream(response.subscriptionId) }
        }
    }

    private fun subscribeToMyOrdersStream(subscriptionId: SubscriptionId) {
        streamServiceStub.getMyOrderUpdates(subscriptionId, object : StreamObserver<MyOrderUpdate> {
            override fun onNext(orderUpdate: MyOrderUpdate) {
                val empty = ordersRecyclerAdapter?.updateOrder(orderUpdate, model.getCompanyNameFromStockId(orderUpdate.stockId))
                flipVisibilities(empty)
            }

            override fun onError(t: Throwable) {
            }

            override fun onCompleted() {
            }
        })
    }

    private fun getOpenOrdersAsynchronously() {
        loadingOrdersDialog.show()
        doAsync {
            if (ConnectionUtils.getConnectionInfo(context)) {
                if (ConnectionUtils.isReachableByTcp(Constants.HOST, Constants.PORT)) {
                    val openOrdersResponse = actionServiceBlockingStub.getMyOpenOrders(GetMyOpenOrdersRequest.newBuilder().build())

                    uiThread {

                        if (openOrdersResponse?.statusCode == GetMyOpenOrdersResponse.StatusCode.OK) {
                            ordersRecycler_swipeRefreshLayout.isRefreshing = false

                            val openOrdersList = mutableListOf<Order>()

                            val askList = openOrdersResponse.openAskOrdersList
                            val bidList = openOrdersResponse.openBidOrdersList

                            if (askList.size > 0) {
                                for (currentAskOrder in askList) {
                                    openOrdersList.add(Order(
                                            currentAskOrder.id,
                                            false,
                                            false,
                                            currentAskOrder.price,
                                            currentAskOrder.stockId,
                                            model.getCompanyNameFromStockId(currentAskOrder.stockId),
                                            currentAskOrder.orderType,
                                            currentAskOrder.stockQuantity,
                                            currentAskOrder.stockQuantityFulfilled
                                    ))
                                }
                            }

                            if (bidList.size > 0) {
                                for (currentBidOrder in bidList) {
                                    openOrdersList.add(Order(
                                            currentBidOrder.id,
                                            true,
                                            false,
                                            currentBidOrder.price,
                                            currentBidOrder.stockId,
                                            model.getCompanyNameFromStockId(currentBidOrder.stockId),
                                            currentBidOrder.orderType,
                                            currentBidOrder.stockQuantity,
                                            currentBidOrder.stockQuantityFulfilled
                                    ))
                                }
                            }

                            val empty = ordersRecyclerAdapter?.swapData(openOrdersList)
                            flipVisibilities(empty)

                        } else {
                            context?.longToast(openOrdersResponse.statusMessage)
                        }
                    }
                } else {
                    uiThread { networkDownHandler?.onNetworkDownError(resources.getString(R.string.error_server_down), R.id.open_orders_dest) }
                }
            } else {
                uiThread { networkDownHandler?.onNetworkDownError(resources.getString(R.string.error_check_internet), R.id.open_orders_dest) }
            }
            uiThread { loadingOrdersDialog.dismiss() }
        }
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int, position: Int) {

        val orderId = ordersRecyclerAdapter?.getOrderIdFromPosition(position) ?: -1
        val isBid = ordersRecyclerAdapter?.getIsBidFromPosition(position) ?: false

        if (orderId == -1) context?.toast("Order does not exist")

        if (context != null) {
            val builder = AlertDialog.Builder(context!!, R.style.AlertDialogTheme)
                    .setTitle("Cancel Confirm")
                    .setCancelable(true)
                    .setMessage("Do you want to cancel this order ?")
                    .setPositiveButton("Yes") { _, _ -> cancelOrder(orderId, isBid) }
                    .setNegativeButton("No") { dialogInterface, _ ->
                        dialogInterface.dismiss()
                        ordersRecyclerAdapter?.notifyItemChanged(position)
                    }
            builder.show()
        }
    }

    private fun cancelOrder(orderId: Int, bid: Boolean) = lifecycleScope.launch {
        loadingOrdersDialog.show()

        if (withContext(Dispatchers.IO) { ConnectionUtils.getConnectionInfo(context) }) {
            if (withContext(Dispatchers.IO) { ConnectionUtils.isReachableByTcp(Constants.HOST, Constants.PORT) }) {

                val response = withContext(Dispatchers.IO) {
                    actionServiceBlockingStub.cancelOrder(
                            CancelOrderRequest.newBuilder().setOrderId(orderId).setIsAsk(!bid).build())
                }

                if (response.statusCode == CancelOrderResponse.StatusCode.OK) {
                    context?.toast("Order cancelled")
                    val empty = ordersRecyclerAdapter?.cancelOrder(orderId)
                    flipVisibilities(empty)
                } else {
                    context?.toast(response.statusMessage)
                }

            } else {
                networkDownHandler?.onNetworkDownError(resources.getString(R.string.error_server_down), R.id.open_orders_dest)
            }
        } else {
            networkDownHandler?.onNetworkDownError(resources.getString(R.string.error_check_internet), R.id.open_orders_dest)
        }

        loadingOrdersDialog.dismiss()
    }

    private fun flipVisibilities(empty: Boolean?) {
        if (empty != null && !empty) {
            ordersRecycler_swipeRefreshLayout.visibility = View.VISIBLE
            emptyOrders_relativeLayout.visibility = View.GONE
        } else {
            ordersRecycler_swipeRefreshLayout.visibility = View.GONE
            emptyOrders_relativeLayout.visibility = View.VISIBLE
        }
    }

    override fun onResume() {
        super.onResume()
        getMyOrdersSubscriptionId()
    }

    override fun onPause() {
        super.onPause()
        loadingOrdersDialog.dismiss()
        doAsync {
            if (orderSubscriptionId != null) {
                streamServiceBlockingStub.unsubscribe(UnsubscribeRequest.newBuilder().setSubscriptionId(orderSubscriptionId).build())
            }
        }
    }

    override fun onRefresh() = getOpenOrdersAsynchronously()
}