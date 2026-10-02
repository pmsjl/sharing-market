/** Remove private navigation context from both regular and hash-router URLs. */
export const buildPublicShareUrl = (href: string): string => {
  const url = new URL(href);
  for (const key of ["from", "conversationId"]) url.searchParams.delete(key);
  const queryStart = url.hash.indexOf("?");
  if (queryStart !== -1) {
    const path = url.hash.slice(0, queryStart);
    const query = new URLSearchParams(url.hash.slice(queryStart + 1));
    for (const key of ["from", "conversationId"]) query.delete(key);
    url.hash = path + (query.toString() ? `?${query.toString()}` : "");
  }
  return url.toString();
};
