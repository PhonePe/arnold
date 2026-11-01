# Review groups

Some changes only make sense together: an API change and its clients, a schema migration and the services that use
it, a rollout spread across repositories. A **review group** bundles related change requests so Arnold can review them
as one unit.

## Create a group

In **Merge Requests**, choose **Create group**, name it (e.g. *Auth V2 rollout*), and pick at least two synced open
change requests. They can come from different repositories. A change request can belong to several groups.

Groups appear under **Review groups** in your inbox. Removing a group can be undone right after.

## Analyse a group

Use **Analyse group** on the group, or **Run Analysis → Change group**. Context, personas and model work as for a
single change.

The `group_review` workflow:

1. checks out every member at its pinned head commit (separate checkouts, even for MRs in the same repository);
2. reviews each member with shared group context, checkpointing each completed member review;
3. runs a **combined review** that looks for cross-repository problems: contract mismatches, ordering and rollout
   hazards, inconsistent changes;
4. consolidates evidence and filters out issues already raised in existing discussions;
5. publishes findings to the relevant change requests.

If any required review fails, nothing is published.

## Retries

From [Analysis History](analysis-history.md):

- **Retry analysis** reuses the original context, persona versions and pinned commits, and re-runs only members that
  did not finish, then the combined review.
- **Retry publication** re-posts saved findings without another AI review. Arnold looks for its hidden markers in
  existing discussions first, so a retry does not post duplicates; uncertain posts are reported as *unknown* rather
  than risked.

If a member gets new commits or the group's membership changes, remaining publication stops and a fresh analysis is
needed. Group jobs are limited to 2 h 45 min; Arnold expires interrupted executions after three hours.

## Requirements

The runner credential must be able to read every member repository: for GitLab, `GITLAB_ACCESS_TOKEN` needs access
to all of them.
