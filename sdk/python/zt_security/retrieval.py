"""Vendor-independent retrieval contract. Stitch facade remains compatible."""
from .stitch import StitchRetrieval, StitchAccessDenied


class RetrievalClient(StitchRetrieval):
    ROOT = '/v1/integrations/retrieval'


RetrievalAccessDenied = StitchAccessDenied
