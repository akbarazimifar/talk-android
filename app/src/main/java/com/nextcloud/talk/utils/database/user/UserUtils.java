/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * Copyright (C) 2017 Mario Danic (mario@lovelyhq.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.nextcloud.talk.utils.database.user;

import android.text.TextUtils;

import com.nextcloud.talk.models.database.User;
import com.nextcloud.talk.models.database.UserEntity;

import java.util.List;

import androidx.annotation.Nullable;
import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;
import io.requery.Persistable;
import io.requery.query.Result;
import io.requery.reactivex.ReactiveEntityStore;

/**
 * @deprecated use {@link com.nextcloud.talk.users.UserManager} instead.
 *
 * TODO: remove this class with a major version, 15.0.0 or 16.0.0.
 */
@Deprecated
public class UserUtils {
    private ReactiveEntityStore<Persistable> dataStore;

    UserUtils(ReactiveEntityStore<Persistable> dataStore) {
        this.dataStore = dataStore;
    }

    private boolean anyUserExists() {
        return (dataStore.count(User.class).where(UserEntity.SCHEDULED_FOR_DELETION.notEqual(Boolean.TRUE))
            .limit(1).get().value() > 0);
    }

    private boolean hasMultipleUsers() {
        return (dataStore.count(User.class).where(UserEntity.SCHEDULED_FOR_DELETION.notEqual(Boolean.TRUE))
            .get().value() > 1);
    }

    private List getUsers() {
        Result findUsersQueryResult = dataStore.select(User.class).where
            (UserEntity.SCHEDULED_FOR_DELETION.notEqual(Boolean.TRUE)).get();

        return findUsersQueryResult.toList();
    }

    private List getUsersScheduledForDeletion() {
        Result findUsersQueryResult = dataStore.select(User.class)
            .where(UserEntity.SCHEDULED_FOR_DELETION.eq(Boolean.TRUE)).get();

        return findUsersQueryResult.toList();
    }


    private UserEntity getAnyUserAndSetAsActive() {
        Result findUserQueryResult = dataStore.select(User.class)
            .where(UserEntity.SCHEDULED_FOR_DELETION.notEqual(Boolean.TRUE))
            .limit(1).get();

        UserEntity userEntity;
        if ((userEntity = (UserEntity) findUserQueryResult.firstOrNull()) != null) {
            userEntity.setCurrent(true);
            dataStore.update(userEntity).blockingGet();
            return userEntity;
        }

        return null;
    }

    private Completable deleteUser(long internalId) {
        Result findUserQueryResult = dataStore.select(User.class).where(UserEntity.ID.eq(internalId)).limit(1).get();

        UserEntity user = (UserEntity) findUserQueryResult.firstOrNull();

        return dataStore.delete(user)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread());

    }

    private Completable deleteUserWithId(long id) {
        Result findUserQueryResult = dataStore.select(User.class).where(UserEntity.ID.eq(id)).limit(1).get();

        UserEntity user = (UserEntity) findUserQueryResult.firstOrNull();

        return dataStore.delete(user)
            .subscribeOn(Schedulers.io());

    }

    private UserEntity getUserById(String id) {
        Result findUserQueryResult = dataStore.select(User.class).where(UserEntity.USER_ID.eq(id))
            .limit(1).get();

        return (UserEntity) findUserQueryResult.firstOrNull();
    }

    private UserEntity getUserWithId(long id) {
        Result findUserQueryResult = dataStore.select(User.class).where(UserEntity.ID.eq(id))
            .limit(1).get();

        return (UserEntity) findUserQueryResult.firstOrNull();
    }


    private void disableAllUsersWithoutId(long userId) {
        Result findUserQueryResult = dataStore.select(User.class).where(UserEntity.ID.notEqual(userId)).get();

        for (Object object : findUserQueryResult) {
            UserEntity userEntity = (UserEntity) object;
            userEntity.setCurrent(false);
            dataStore.update(userEntity).blockingGet();
        }
    }

    private boolean checkIfUserIsScheduledForDeletion(String username, String server) {
        Result findUserQueryResult = dataStore.select(User.class).where(UserEntity.USERNAME.eq(username))
            .and(UserEntity.BASE_URL.eq(server))
            .limit(1).get();

        UserEntity userEntity;
        if ((userEntity = (UserEntity) findUserQueryResult.firstOrNull()) != null) {
            return userEntity.getScheduledForDeletion();
        }

        return false;
    }

    private UserEntity getUserWithInternalId(long internalId) {
        Result findUserQueryResult = dataStore.select(User.class).where(UserEntity.ID.eq(internalId)
                                                                            .and(UserEntity.SCHEDULED_FOR_DELETION.notEqual(Boolean.TRUE)))
            .limit(1).get();

        return (UserEntity) findUserQueryResult.firstOrNull();
    }

    private boolean getIfUserWithUsernameAndServer(String username, String server) {
        Result findUserQueryResult = dataStore.select(User.class).where(UserEntity.USERNAME.eq(username)
                                                                            .and(UserEntity.BASE_URL.eq(server)))
            .limit(1).get();

        return findUserQueryResult.firstOrNull() != null;
    }

    private boolean scheduleUserForDeletionWithId(long id) {
        Result findUserQueryResult = dataStore.select(User.class).where(UserEntity.ID.eq(id))
            .limit(1).get();

        UserEntity userEntity;
        if ((userEntity = (UserEntity) findUserQueryResult.firstOrNull()) != null) {
            userEntity.setScheduledForDeletion(true);
            userEntity.setCurrent(false);
            dataStore.update(userEntity).blockingGet();
        }

        return getAnyUserAndSetAsActive() != null;
    }

    private Observable<UserEntity> createOrUpdateUser(@Nullable String username, @Nullable String token,
                                                     @Nullable String serverUrl,
                                                     @Nullable String displayName,
                                                     @Nullable String pushConfigurationState,
                                                     @Nullable Boolean currentUser,
                                                     @Nullable String userId,
                                                     @Nullable Long internalId,
                                                     @Nullable String capabilities,
                                                     @Nullable String certificateAlias,
                                                     @Nullable String externalSignalingServer) {
        Result findUserQueryResult;
        if (internalId == null) {
            findUserQueryResult = dataStore.select(User.class).where(UserEntity.USERNAME.eq(username).
                                                                         and(UserEntity.BASE_URL.eq(serverUrl))).limit(1).get();
        } else {
            findUserQueryResult = dataStore.select(User.class).where(UserEntity.ID.eq(internalId)).get();
        }

        UserEntity user = (UserEntity) findUserQueryResult.firstOrNull();

        if (user == null) {
            user = new UserEntity();
            user.setBaseUrl(serverUrl);
            user.setUsername(username);
            user.setToken(token);

            if (!TextUtils.isEmpty(displayName)) {
                user.setDisplayName(displayName);
            }

            if (pushConfigurationState != null) {
                user.setPushConfigurationState(pushConfigurationState);
            }

            if (!TextUtils.isEmpty(userId)) {
                user.setUserId(userId);
            }

            if (!TextUtils.isEmpty(capabilities)) {
                user.setCapabilities(capabilities);
            }

            if (!TextUtils.isEmpty(certificateAlias)) {
                user.setClientCertificate(certificateAlias);
            }

            if (!TextUtils.isEmpty(externalSignalingServer)) {
                user.setExternalSignalingServer(externalSignalingServer);
            }

            user.setCurrent(true);

        } else {
            if (userId != null && (user.getUserId() == null || !user.getUserId().equals(userId))) {
                user.setUserId(userId);
            }

            if (token != null && !token.equals(user.getToken())) {
                user.setToken(token);
            }

            if ((displayName != null && user.getDisplayName() == null) || (displayName != null && user.getDisplayName()
                != null && !displayName.equals(user.getDisplayName()))) {
                user.setDisplayName(displayName);
            }

            if (pushConfigurationState != null && !pushConfigurationState.equals(user.getPushConfigurationState())) {
                user.setPushConfigurationState(pushConfigurationState);
            }

            if (capabilities != null && !capabilities.equals(user.getCapabilities())) {
                user.setCapabilities(capabilities);
            }

            if (certificateAlias != null && !certificateAlias.equals(user.getClientCertificate())) {
                user.setClientCertificate(certificateAlias);
            }

            if (externalSignalingServer != null && !externalSignalingServer.equals(user.getExternalSignalingServer())) {
                user.setExternalSignalingServer(externalSignalingServer);
            }

            if (currentUser != null) {
                user.setCurrent(currentUser);
            }
        }

        return dataStore.upsert(user)
            .toObservable()
            .subscribeOn(Schedulers.io());
    }
}
