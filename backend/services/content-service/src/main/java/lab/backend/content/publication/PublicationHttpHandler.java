package lab.backend.content.publication;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lab.backend.content.transport.WireCodec;

/**
 * INT-03 trả exact stored bytes. Manifest lớn đi qua subresource body riêng;
 * bundle response chỉ mang digest/length/link bounded nên không phụ thuộc giới
 * hạn response-header của container hoặc proxy.
 */
public final class PublicationHttpHandler {
  public static final String PREFIX = "/internal/v1/content-bundles/";
  private static final String MANIFEST_SUFFIX = "/manifest";
  private final PublicationStore store;

  public PublicationHttpHandler(PublicationStore store) {
    this.store = store;
  }

  public boolean handle(HttpServletRequest request, HttpServletResponse response, String caller)
      throws IOException {
    String path = request.getRequestURI();
    if (!path.startsWith(PREFIX)) return false;
    try {
      if (!"learning".equals(caller)) throw new WireCodec.Denied(403, "ACTION_DENIED");
      if (!"GET".equals(request.getMethod())) throw new WireCodec.Denied(405, "METHOD_DENIED");

      String resource = path.substring(PREFIX.length());
      boolean manifestRequest = resource.endsWith(MANIFEST_SUFFIX);
      String id = manifestRequest
          ? resource.substring(0, resource.length() - MANIFEST_SUFFIX.length())
          : resource;
      if (id.isBlank() || !id.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")) {
        throw new WireCodec.Denied(404, "BUNDLE_NOT_FOUND");
      }
      PublicationStore.Bundle bundle = store.get(id);
      if (bundle == null) throw new WireCodec.Denied(404, "BUNDLE_NOT_FOUND");

      response.setStatus(200);
      response.setHeader("Cache-Control", "no-store");
      response.setHeader("X-Content-Contract-Version", "1");
      response.setHeader("X-Content-Revision", Long.toString(bundle.revision()));
      if (manifestRequest) {
        writeManifest(response, bundle);
      } else {
        writeBundle(response, bundle);
      }
      return true;
    } catch (WireCodec.Denied denied) {
      response.setStatus(denied.status);
      response.setContentType("application/json");
      response.getOutputStream().write(WireCodec.JSON.writeValueAsBytes(WireCodec.error(denied.code)));
      return true;
    } catch (Exception failure) {
      response.setStatus(503);
      response.setContentType("application/json");
      response.getOutputStream().write(
          WireCodec.JSON.writeValueAsBytes(WireCodec.error("BUNDLE_UNAVAILABLE")));
      return true;
    }
  }

  private static void writeBundle(HttpServletResponse response, PublicationStore.Bundle bundle)
      throws IOException {
    response.setContentType("application/octet-stream");
    response.setHeader("Digest", "sha-256=" + bundle.digest());
    response.setHeader("X-Content-Manifest-Digest", "sha-256=" + bundle.manifestDigest());
    response.setHeader("X-Content-Manifest-Length", Integer.toString(bundle.manifest().length));
    response.setHeader("Link", "<" + PREFIX + bundle.id() + MANIFEST_SUFFIX
        + ">; rel=\"manifest\"; type=\"application/json\"");
    response.setContentLength(bundle.bytes().length);
    response.getOutputStream().write(bundle.bytes());
  }

  private static void writeManifest(HttpServletResponse response, PublicationStore.Bundle bundle)
      throws IOException {
    response.setContentType("application/json");
    response.setHeader("Digest", "sha-256=" + bundle.manifestDigest());
    response.setHeader("X-Content-Bundle-Digest", "sha-256=" + bundle.digest());
    response.setContentLength(bundle.manifest().length);
    response.getOutputStream().write(bundle.manifest());
  }
}
