"""PocketFlow graph for Arnold Smart Diff generation."""

from pocketflow import Flow

from flows.code_reviews.common.nodes import (
    CheckoutBranch,
    Cleanup,
    CloneRepository,
    FetchJiraTickets,
    FetchRFCContent,
    GetImpactContext,
)
from flows.code_reviews.smart_diff.nodes import (
    CompleteSmartDiff,
    GenerateSmartDiffArtifacts,
    GetSmartDiffChanges,
    StartSmartDiff,
)


def create_smart_diff_flow():
    start = StartSmartDiff()
    clone = CloneRepository()
    checkout = CheckoutBranch()
    get_changes = GetSmartDiffChanges()
    get_impact = GetImpactContext()
    fetch_jira = FetchJiraTickets()
    fetch_rfc = FetchRFCContent()
    generate = GenerateSmartDiffArtifacts()
    complete = CompleteSmartDiff()
    cleanup = Cleanup()

    (start >> clone >> checkout >> get_changes >> get_impact >> fetch_jira >> fetch_rfc
     >> generate >> complete >> cleanup)

    return Flow(start=start)
