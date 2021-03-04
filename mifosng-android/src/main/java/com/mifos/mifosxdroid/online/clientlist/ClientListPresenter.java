package com.mifos.mifosxdroid.online.clientlist;

import android.content.Context;

import androidx.appcompat.view.ActionMode;

import com.mifos.api.datamanager.DataManagerClient;
import com.mifos.api.datamanager.DataManagerOffices;
import com.mifos.mifosxdroid.R;
import com.mifos.mifosxdroid.base.BasePresenter;
import com.mifos.mifosxdroid.injection.ActivityContext;
import com.mifos.objects.client.Client;
import com.mifos.objects.client.Page;
import com.mifos.objects.organisation.Office;
import com.mifos.objects.templates.clients.OfficeOptions;
import com.mifos.utils.EspressoIdlingResource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.inject.Inject;

import rx.Observable;
import rx.Subscriber;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.schedulers.Schedulers;
import rx.subscriptions.CompositeSubscription;

/**
 * Created by Rajan Maurya on 6/6/16.
 * This Presenter Holds the All Logic to request to DataManagerClient and DataManagerClient, Take
 * care of that From Where Data will come Database or REST API.
 */
public class ClientListPresenter extends BasePresenter<ClientListMvpView> {

    private static final String LOG_TAG = ClientListPresenter.class.getSimpleName();

    private final DataManagerClient mDataManagerClient;
    private final DataManagerOffices mDataManagerOffices;
    private CompositeSubscription mSubscriptions;
    private Context c;

    private List<Client> mDbClientList;
    private List<Client> mSyncClientList;

    private int officeId = 1;
    private Boolean loadmore = false;
    private Boolean mRestApiClientSyncStatus = false;
    private Boolean mDatabaseClientSyncStatus = false;

    @Inject
    public ClientListPresenter(DataManagerClient dataManagerClient, DataManagerOffices dataManagerOffices, @ActivityContext Context context) {
        mDataManagerClient = dataManagerClient;
        mDataManagerOffices = dataManagerOffices;
        c = context;
        mDbClientList = new ArrayList<>();
        mSyncClientList = new ArrayList<>();
    }

    @Override
    public void attachView(ClientListMvpView mvpView) {
        super.attachView(mvpView);
        mSubscriptions = new CompositeSubscription();

    }

    @Override
    public void detachView() {
        super.detachView();
        mSubscriptions.unsubscribe();
    }

    /**
     * Loading Client List from Rest API and setting loadmore status
     *
     * @param loadmore Status, need ClientList page other then first.
     * @param offset   Index from Where ClientList will be fetched.
     */
    public void loadClients(Boolean loadmore, int offset) {
        this.loadmore = loadmore;
        loadClients(true, offset, officeId);
    }

    /**
     * Showing Client List in View, If loadmore is true call showLoadMoreClients(...) and else
     * call showClientList(...).
     */
    public void showClientList(List<Client> clients) {
        if (loadmore) {
            getMvpView().showLoadMoreClients(clients);
        } else {
            getMvpView().showClientList(clients);
        }
    }

    /**
     * This Method will called, when Parent (Fragment or Activity) will be true.
     * If Parent Fragment is true there is no need to fetch ClientList, Just show the Parent
     * (Fragment or Activity) ClientList in View
     *
     * @param clients List<Client></>
     */
    public void showParentClients(List<Client> clients) {
        getMvpView().unregisterSwipeAndScrollListener();
        if (clients.size() == 0) {
            getMvpView().showEmptyClientList(R.string.client);
        } else {
            mRestApiClientSyncStatus = true;
            mSyncClientList = clients;
            setAlreadyClientSyncStatus();
        }
    }

    /**
     * Setting ClientSync Status True when mRestApiClientSyncStatus && mDatabaseClientSyncStatus
     * are true.
     */
    public void setAlreadyClientSyncStatus() {
        if (mRestApiClientSyncStatus && mDatabaseClientSyncStatus) {
            showClientList(checkClientAlreadySyncedOrNot(mSyncClientList));
        }
    }

    /**
     * This Method fetching Client List from Rest API.
     *
     * @param paged  True Enabling the Pagination of the API
     * @param offset Value give from which position Fetch ClientList
     * @param limit  Maximum size of the Center
     */
    public void loadClients(boolean paged, int offset, int officeId) {
        EspressoIdlingResource.increment(); // App is busy until further notice.
        checkViewAttached();
        getMvpView().showProgressbar(true);
        mSubscriptions.add(mDataManagerClient.getAllClientsByOfficeId(paged, offset, officeId)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeOn(Schedulers.io())
                .subscribe(new Subscriber<Page<Client>>() {
                    @Override
                    public void onCompleted() {
                        loadOffices();
                    }

                    @Override
                    public void onError(Throwable e) {
                        getMvpView().showProgressbar(false);

                        if (loadmore) {
                            getMvpView().showMessage(R.string.failed_to_load_client);
                        } else {
                            getMvpView().showError();
                        }

                        EspressoIdlingResource.decrement(); // App is idle.

                    }

                    @Override
                    public void onNext(Page<Client> clientPage) {

                        mSyncClientList = clientPage.getPageItems();

                        if (mSyncClientList.size() == 0 && !loadmore) {
                            getMvpView().showEmptyClientList(R.string.client);
                            getMvpView().unregisterSwipeAndScrollListener();
                        } else if (mSyncClientList.size() == 0 && loadmore) {
                            getMvpView().showMessage(R.string.no_more_clients_available);
                        } else {
                            mRestApiClientSyncStatus = true;
                            setAlreadyClientSyncStatus();
                        }
                        getMvpView().showProgressbar(false);

                        EspressoIdlingResource.decrement(); // App is idle.
                    }
                }));
    }

    public void loadClientsByOfficeId(boolean paged, int offset, int officeId) {
        mSubscriptions.add(mDataManagerClient.getAllClientsByOfficeId(paged, offset, officeId)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeOn(Schedulers.io())
                .subscribe(new Subscriber<Page<Client>>() {
                    @Override
                    public void onCompleted() {
                    }

                    @Override
                    public void onError(Throwable e) {
                        getMvpView().showProgressbar(false);

                        if (loadmore) {
                            getMvpView().showMessage(R.string.failed_to_load_client);
                        } else {
                            getMvpView().showError();
                        }
                        EspressoIdlingResource.decrement(); // App is idle.
                    }

                    @Override
                    public void onNext(Page<Client> clientPage) {
                        mSyncClientList = clientPage.getPageItems();
                        getMvpView().showClientList(mSyncClientList);
                    }
                }));
    }


    public void loadOffices() {
        checkViewAttached();
        mSubscriptions.add(mDataManagerOffices.getOfficesFields()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeOn(Schedulers.io())
                .subscribe(new Subscriber<List<OfficeOptions>>() {
                    @Override
                    public void onCompleted() {
                    }

                    @Override
                    public void onError(Throwable e) {
                        getMvpView().showMessage(R.string.failed_to_fetch_offices);
                    }

                    @Override
                    public void onNext(List<OfficeOptions> officeOptions) {
                        getMvpView().showOffices(officeOptions);
                    }
                }));
    }

    HashMap<String, Integer> createOfficeNameIdMap(List<OfficeOptions> offices,
                                                   final List<String> officeNames) {
        final HashMap<String, Integer> officeMap = new HashMap<>();

        officeMap.put(c.getResources().getString(R.string.spinner_office), -1);
        officeNames.clear();
        officeNames.add(c.getResources().getString(R.string.spinner_office));
        Observable.from(offices)
                .subscribe(new Action1<OfficeOptions>() {
                    @Override
                    public void call(OfficeOptions office) {
                        officeMap.put(office.getName(), office.getId());
                        officeNames.add(office.getName());
                    }
                });
        return officeMap;
    }


    /**
     * This Method Loading the Client From Database. It request Observable to DataManagerClient
     * and DataManagerClient Request to DatabaseHelperClient to load the Client List Page from the
     * Client_Table and As the Client List Page is loaded DataManagerClient gives the Client List
     * Page after getting response from DatabaseHelperClient
     */
    public void loadDatabaseClients() {
        checkViewAttached();
        mSubscriptions.add(mDataManagerClient.getAllDatabaseClients()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeOn(Schedulers.io())
                .subscribe(new Subscriber<Page<Client>>() {
                    @Override
                    public void onCompleted() {
                    }

                    @Override
                    public void onError(Throwable e) {
                        getMvpView().showMessage(R.string.failed_to_load_db_clients);
                    }

                    @Override
                    public void onNext(Page<Client> clientPage) {
                        mDatabaseClientSyncStatus = true;
                        mDbClientList = clientPage.getPageItems();
                        setAlreadyClientSyncStatus();
                    }
                })
        );
    }


    /**
     * This Method Filtering the Clients Loaded from Server is already sync or not. If yes the
     * put the client.setSync(true) and view will show those clients as sync already to user
     *
     * @param
     * @return Page<Client>
     */
    public List<Client> checkClientAlreadySyncedOrNot(List<Client> clients) {
        if (mDbClientList.size() != 0) {

            for (Client dbClient : mDbClientList) {
                for (Client syncClient : clients) {
                    if (dbClient.getId() == syncClient.getId()) {
                        syncClient.setSync(true);
                        break;
                    }
                }
            }
        }
        return clients;
    }

}
