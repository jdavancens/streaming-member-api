FROM ghcr.io/apollographql/router:v1.55.1

COPY supergraph.graphql /dist/supergraph.graphql
COPY services/router/router.yaml /dist/router.yaml

ENTRYPOINT ["/dist/router", "--config", "/dist/router.yaml", "--supergraph", "/dist/supergraph.graphql"]
