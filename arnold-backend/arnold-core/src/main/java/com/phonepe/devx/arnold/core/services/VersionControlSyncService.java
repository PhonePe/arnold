package com.phonepe.devx.arnold.core.services;

import com.phonepe.devx.arnold.core.model.comment.CommentRecord;
import com.phonepe.devx.arnold.core.model.vcs.ChangeRequestSync;

public interface VersionControlSyncService {
    void synchronize(ChangeRequestSync sync);

    void synchronize(CommentRecord comment);
}
