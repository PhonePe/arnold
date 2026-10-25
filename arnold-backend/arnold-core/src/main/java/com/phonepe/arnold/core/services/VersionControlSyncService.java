package com.phonepe.arnold.core.services;

import com.phonepe.arnold.core.model.comment.CommentRecord;
import com.phonepe.arnold.core.model.vcs.ChangeRequestSync;

public interface VersionControlSyncService {
    boolean synchronize(ChangeRequestSync sync);

    void synchronize(CommentRecord comment);
}
