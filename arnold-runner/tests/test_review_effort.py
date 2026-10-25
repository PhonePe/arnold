from unittest.mock import patch

import pytest
from pydantic import ValidationError

from flows.code_reviews.review.nodes import ExecuteReviewSummary
from flows.code_reviews.review.markdown_formatter import format_review_as_markdown, format_review_effort
from flows.code_reviews.smart_diff.nodes import OrganizeSmartDiff


def test_effort_requires_evidence_and_survives_both_outputs():
    rationale = "src/query.py changes pagination; review cursor boundaries and the repository tests."
    data = {"estimated_effort": 3, "estimated_effort_rationale": rationale}

    def review_agent(*args, tools, **kwargs):
        metadata = tools[0].metadata
        for invalid in [
            {"estimated_effort": 3},
            {**data, "estimated_effort_rationale": "  "},
            {**data, "estimated_effort": 6},
        ]:
            with pytest.raises(ValidationError):
                metadata.params_model(**invalid)
        metadata.handler(metadata.params_model(**data))
        return "Done"

    node = ExecuteReviewSummary()
    prep = node.prep({"changed_files": [{"path": "src/query.py", "status": "M"}]})
    with patch("utils.tool_adapter._HARNESS", "opencode"), patch(
        "flows.code_reviews.review.nodes.call_llm_agentic", side_effect=review_agent
    ):
        review = node.exec(prep)["review_data"]
    assert review["estimated_effort_rationale"] == rationale
    effort = format_review_effort(review)
    assert "Scale:" not in effort
    assert "**Review effort: 3/5**" in effort
    assert f"Basis: {rationale}" in effort
    assert effort in format_review_as_markdown(review)
    with patch("flows.code_reviews.smart_diff.nodes.organize", return_value=("Overview", [])) as organize:
        result = OrganizeSmartDiff().exec({
            "review_data": review, "units": [{"oldStartLine": 1, "newStartLine": 1}]
        })
    assert result["smart_diff_summary"] == f"Overview\n\n{effort}"
    assert "estimated_effort" not in organize.call_args.args[1]

    with patch("flows.code_reviews.review.nodes.call_llm_agentic", return_value="No tool call"):
        fallback = node.exec(prep)["review_data"]
    assert fallback["estimated_effort"] is None
    for unsupported in [fallback, {"estimated_effort": 3}, {**data, "estimated_effort_rationale": " "}]:
        assert format_review_effort(unsupported) == ""
        assert "3/5" not in format_review_as_markdown(unsupported)
    assert "&lt;script&gt;" in format_review_effort({**data, "estimated_effort_rationale": "<script>"})
