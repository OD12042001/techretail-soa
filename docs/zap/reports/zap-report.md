# ZAP Scanning Report

ZAP by [Checkmarx](https://checkmarx.com/).


## Summary of Alerts

| Risk Level | Number of Alerts |
| --- | --- |
| High | 0 |
| Medium | 0 |
| Low | 2 |
| Informational | 2 |




## Insights

| Level | Reason | Site | Description | Statistic |
| --- | --- | --- | --- | --- |
| Medium | Exceeded High | http://host.docker.internal:8090 | Percentage of responses with status code 4xx | 66 % |
| Info | Informational | http://host.docker.internal:8090 | Percentage of responses with status code 2xx | 32 % |
| Info | Informational | http://host.docker.internal:8090 | Percentage of endpoints with content type application/json | 100 % |
| Info | Informational | http://host.docker.internal:8090 | Percentage of endpoints with method GET | 66 % |
| Info | Informational | http://host.docker.internal:8090 | Percentage of endpoints with method POST | 30 % |
| Info | Informational | http://host.docker.internal:8090 | Percentage of endpoints with method PUT | 4 % |
| Info | Informational | http://host.docker.internal:8090 | Count of total endpoints | 50    |
| Info | Exceeded Low | http://host.docker.internal:8090 | Percentage of slow responses | 23 % |







## Alerts

| Name | Risk Level | Number of Instances |
| --- | --- | --- |
| A Server Error response code was returned by the server | Low | 3 |
| Cross-Origin-Resource-Policy Header Missing or Invalid | Low | Systemic |
| A Client Error response code was returned by the server | Informational | 48 |
| Non-Storable Content | Informational | Systemic |




## Alert Detail



### [ A Server Error response code was returned by the server ](https://www.zaproxy.org/docs/alerts/100000/)



##### Low (High)

### Description

A response code of 500 was returned by the server.
This may indicate that the application is failing to handle unexpected input correctly.
Raised by the 'Alert on HTTP Response Code Error' script

* URL: http://host.docker.internal:8090/api/productos
  * Node Name: `http://host.docker.internal:8090/api/productos ()({nombre,descripcion,precio,idCategoria,stockReferencial,disponible})`
  * Method: `POST`
  * Parameter: ``
  * Attack: ``
  * Evidence: `500`
  * Other Info: ``
* URL: http://host.docker.internal:8090/api/productos/categorias
  * Node Name: `http://host.docker.internal:8090/api/productos/categorias ()({nombre,descripcion})`
  * Method: `POST`
  * Parameter: ``
  * Attack: ``
  * Evidence: `500`
  * Other Info: ``
* URL: http://host.docker.internal:8090/api/productos/1
  * Node Name: `http://host.docker.internal:8090/api/productos/1 ()({nombre,descripcion,precio,idCategoria,stockReferencial,disponible})`
  * Method: `PUT`
  * Parameter: ``
  * Attack: ``
  * Evidence: `500`
  * Other Info: ``


Instances: 3

### Solution



### Reference



#### CWE Id: [ 388 ](https://cwe.mitre.org/data/definitions/388.html)


#### WASC Id: 20

#### Source ID: 4

### [ Cross-Origin-Resource-Policy Header Missing or Invalid ](https://www.zaproxy.org/docs/alerts/90004/)



##### Low (Medium)

### Description

Cross-Origin-Resource-Policy header is an opt-in header designed to counter side-channels attacks like Spectre. Resource should be specifically set as shareable amongst different origins.

* URL: http://host.docker.internal:8090/api/productos%3Fcategoria=3&q=monitor&soloDisponibles=false
  * Node Name: `http://host.docker.internal:8090/api/productos (categoria,q,soloDisponibles)`
  * Method: `GET`
  * Parameter: `Cross-Origin-Resource-Policy`
  * Attack: ``
  * Evidence: ``
  * Other Info: ``
* URL: http://host.docker.internal:8090/api/productos/categorias
  * Node Name: `http://host.docker.internal:8090/api/productos/categorias`
  * Method: `GET`
  * Parameter: `Cross-Origin-Resource-Policy`
  * Attack: ``
  * Evidence: ``
  * Other Info: ``
* URL: http://host.docker.internal:8090/api/productos
  * Node Name: `http://host.docker.internal:8090/api/productos ()({nombre,descripcion,precio,idCategoria,stockReferencial,disponible})`
  * Method: `POST`
  * Parameter: `Cross-Origin-Resource-Policy`
  * Attack: ``
  * Evidence: ``
  * Other Info: ``
* URL: http://host.docker.internal:8090/api/productos/categorias
  * Node Name: `http://host.docker.internal:8090/api/productos/categorias ()({nombre,descripcion})`
  * Method: `POST`
  * Parameter: `Cross-Origin-Resource-Policy`
  * Attack: ``
  * Evidence: ``
  * Other Info: ``
* URL: http://host.docker.internal:8090/api/productos/1
  * Node Name: `http://host.docker.internal:8090/api/productos/1 ()({nombre,descripcion,precio,idCategoria,stockReferencial,disponible})`
  * Method: `PUT`
  * Parameter: `Cross-Origin-Resource-Policy`
  * Attack: ``
  * Evidence: ``
  * Other Info: ``

Instances: Systemic


### Solution

Ensure that the application/web server sets the Cross-Origin-Resource-Policy header appropriately, and that it sets the Cross-Origin-Resource-Policy header to 'same-origin' for all web pages.
'same-site' is considered as less secured and should be avoided.
If resources must be shared, set the header to 'cross-origin'.
If possible, ensure that the end user uses a standards-compliant and modern web browser that supports the Cross-Origin-Resource-Policy header (https://caniuse.com/mdn-http_headers_cross-origin-resource-policy).

### Reference


* [ https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Headers/Cross-Origin-Embedder-Policy ](https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Headers/Cross-Origin-Embedder-Policy)


#### CWE Id: [ 693 ](https://cwe.mitre.org/data/definitions/693.html)


#### WASC Id: 14

#### Source ID: 3

### [ A Client Error response code was returned by the server ](https://www.zaproxy.org/docs/alerts/100000/)



##### Informational (High)

### Description

A response code of 400 was returned by the server.
This may indicate that the application is failing to handle unexpected input correctly.
Raised by the 'Alert on HTTP Response Code Error' script

* URL: http://host.docker.internal:8090
  * Node Name: `http://host.docker.internal:8090`
  * Method: `GET`
  * Parameter: ``
  * Attack: ``
  * Evidence: `404`
  * Other Info: ``
* URL: http://host.docker.internal:8090/
  * Node Name: `http://host.docker.internal:8090/`
  * Method: `GET`
  * Parameter: ``
  * Attack: ``
  * Evidence: `404`
  * Other Info: ``
* URL: http://host.docker.internal:8090/6761802650462138498
  * Node Name: `http://host.docker.internal:8090/6761802650462138498`
  * Method: `GET`
  * Parameter: ``
  * Attack: ``
  * Evidence: `404`
  * Other Info: ``
* URL: http://host.docker.internal:8090/api
  * Node Name: `http://host.docker.internal:8090/api`
  * Method: `GET`
  * Parameter: ``
  * Attack: ``
  * Evidence: `404`
  * Other Info: ``
* URL: http://host.docker.internal:8090/api/
  * Node Name: `http://host.docker.internal:8090/api/`
  * Method: `GET`
  * Parameter: ``
  * Attack: ``
  * Evidence: `404`
  * Other Info: ``
* URL: http://host.docker.internal:8090/api/4798685696614062524
  * Node Name: `http://host.docker.internal:8090/api/4798685696614062524`
  * Method: `GET`
  * Parameter: ``
  * Attack: ``
  * Evidence: `404`
  * Other Info: ``
* URL: http://host.docker.internal:8090/api/auditoria%3F=
  * Node Name: `http://host.docker.internal:8090/api/auditoria`
  * Method: `GET`
  * Parameter: ``
  * Attack: ``
  * Evidence: `400`
  * Other Info: ``
* URL: http://host.docker.internal:8090/api/auditoria%3Flimite=http%253A%252F%252Fwww.google.com%252F
  * Node Name: `http://host.docker.internal:8090/api/auditoria (limite)`
  * Method: `GET`
  * Parameter: ``
  * Attack: ``
  * Evidence: `400`
  * Other Info: ``
* URL: http://host.docker.internal:8090/api/auditoria/
  * Node Name: `http://host.docker.internal:8090/api/auditoria/`
  * Method: `GET`
  * Parameter: ``
  * Attack: ``
  * Evidence: `404`
  * Other Info: ``
* URL: http://host.docker.internal:8090/api/auditoria/771962756498512570
  * Node Name: `http://host.docker.internal:8090/api/auditoria/771962756498512570`
  * Method: `GET`
  * Parameter: ``
  * Attack: ``
  * Evidence: `404`
  * Other Info: ``
* URL: http://host.docker.internal:8090/api/auditoria/resumen/
  * Node Name: `http://host.docker.internal:8090/api/auditoria/resumen/`
  * Method: `GET`
  * Parameter: ``
  * Attack: ``
  * Evidence: `404`
  * Other Info: ``
* URL: http://host.docker.internal:8090/api/auth
  * Node Name: `http://host.docker.internal:8090/api/auth`
  * Method: `GET`
  * Parameter: ``
  * Attack: ``
  * Evidence: `401`
  * Other Info: ``
* URL: http://host.docker.internal:8090/api/auth/
  * Node Name: `http://host.docker.internal:8090/api/auth/`
  * Method: `GET`
  * Parameter: ``
  * Attack: ``
  * Evidence: `401`
  * Other Info: ``
* URL: http://host.docker.internal:8090/api/auth/3544665475631529011
  * Node Name: `http://host.docker.internal:8090/api/auth/3544665475631529011`
  * Method: `GET`
  * Parameter: ``
  * Attack: ``
  * Evidence: `401`
  * Other Info: ``
* URL: http://host.docker.internal:8090/api/auth/actuator/health
  * Node Name: `http://host.docker.internal:8090/api/auth/actuator/health`
  * Method: `GET`
  * Parameter: ``
  * Attack: ``
  * Evidence: `401`
  * Other Info: ``
* URL: http://host.docker.internal:8090/api/monitoreo
  * Node Name: `http://host.docker.internal:8090/api/monitoreo`
  * Method: `GET`
  * Parameter: ``
  * Attack: ``
  * Evidence: `404`
  * Other Info: ``
* URL: http://host.docker.internal:8090/api/monitoreo/
  * Node Name: `http://host.docker.internal:8090/api/monitoreo/`
  * Method: `GET`
  * Parameter: ``
  * Attack: ``
  * Evidence: `404`
  * Other Info: ``
* URL: http://host.docker.internal:8090/api/monitoreo/5959007742564124330
  * Node Name: `http://host.docker.internal:8090/api/monitoreo/5959007742564124330`
  * Method: `GET`
  * Parameter: ``
  * Attack: ``
  * Evidence: `404`
  * Other Info: ``
* URL: http://host.docker.internal:8090/api/monitoreo/estado/
  * Node Name: `http://host.docker.internal:8090/api/monitoreo/estado/`
  * Method: `GET`
  * Parameter: ``
  * Attack: ``
  * Evidence: `404`
  * Other Info: ``
* URL: http://host.docker.internal:8090/api/monitoreo/historial%3F=
  * Node Name: `http://host.docker.internal:8090/api/monitoreo/historial`
  * Method: `GET`
  * Parameter: ``
  * Attack: ``
  * Evidence: `400`
  * Other Info: ``
* URL: http://host.docker.internal:8090/api/monitoreo/historial%3Flimite=http%253A%252F%252Fwww.google.com%252F
  * Node Name: `http://host.docker.internal:8090/api/monitoreo/historial (limite)`
  * Method: `GET`
  * Parameter: ``
  * Attack: ``
  * Evidence: `400`
  * Other Info: ``
* URL: http://host.docker.internal:8090/api/monitoreo/historial/
  * Node Name: `http://host.docker.internal:8090/api/monitoreo/historial/`
  * Method: `GET`
  * Parameter: ``
  * Attack: ``
  * Evidence: `404`
  * Other Info: ``
* URL: http://host.docker.internal:8090/api/productos%3Fcategoria=3&q=monitor&=
  * Node Name: `http://host.docker.internal:8090/api/productos (categoria,q)`
  * Method: `GET`
  * Parameter: ``
  * Attack: ``
  * Evidence: `400`
  * Other Info: ``
* URL: http://host.docker.internal:8090/api/productos%3Fcategoria=http%253A%252F%252Fwww.google.com%252F&q=monitor&soloDisponibles=false
  * Node Name: `http://host.docker.internal:8090/api/productos (categoria,q,soloDisponibles)`
  * Method: `GET`
  * Parameter: ``
  * Attack: ``
  * Evidence: `400`
  * Other Info: ``
* URL: http://host.docker.internal:8090/api/productos%3Fcategoria=3&=&soloDisponibles=false
  * Node Name: `http://host.docker.internal:8090/api/productos (categoria,soloDisponibles)`
  * Method: `GET`
  * Parameter: ``
  * Attack: ``
  * Evidence: `400`
  * Other Info: ``
* URL: http://host.docker.internal:8090/api/productos%3F=&q=monitor&soloDisponibles=false
  * Node Name: `http://host.docker.internal:8090/api/productos (q,soloDisponibles)`
  * Method: `GET`
  * Parameter: ``
  * Attack: ``
  * Evidence: `400`
  * Other Info: ``
* URL: http://host.docker.internal:8090/api/productos/
  * Node Name: `http://host.docker.internal:8090/api/productos/`
  * Method: `GET`
  * Parameter: ``
  * Attack: ``
  * Evidence: `404`
  * Other Info: ``
* URL: http://host.docker.internal:8090/api/productos/1/
  * Node Name: `http://host.docker.internal:8090/api/productos/1/`
  * Method: `GET`
  * Parameter: ``
  * Attack: ``
  * Evidence: `404`
  * Other Info: ``
* URL: http://host.docker.internal:8090/api/productos/8370267959800866208
  * Node Name: `http://host.docker.internal:8090/api/productos/8370267959800866208`
  * Method: `GET`
  * Parameter: ``
  * Attack: ``
  * Evidence: `404`
  * Other Info: ``
* URL: http://host.docker.internal:8090/api/productos/categorias/
  * Node Name: `http://host.docker.internal:8090/api/productos/categorias/`
  * Method: `GET`
  * Parameter: ``
  * Attack: ``
  * Evidence: `404`
  * Other Info: ``
* URL: http://host.docker.internal:8090/api/auth/login
  * Node Name: `http://host.docker.internal:8090/api/auth/login ()({usuario,password})`
  * Method: `POST`
  * Parameter: ``
  * Attack: ``
  * Evidence: `401`
  * Other Info: ``
* URL: http://host.docker.internal:8090/api/auth/login/
  * Node Name: `http://host.docker.internal:8090/api/auth/login/ ()({usuario,password})`
  * Method: `POST`
  * Parameter: ``
  * Attack: ``
  * Evidence: `404`
  * Other Info: ``
* URL: http://host.docker.internal:8090/api/auth/registro
  * Node Name: `http://host.docker.internal:8090/api/auth/registro ()({usuario,password,nombre})`
  * Method: `POST`
  * Parameter: ``
  * Attack: ``
  * Evidence: `400`
  * Other Info: ``
* URL: http://host.docker.internal:8090/api/auth/registro
  * Node Name: `http://host.docker.internal:8090/api/auth/registro ()({usuario,password,nombre})`
  * Method: `POST`
  * Parameter: ``
  * Attack: ``
  * Evidence: `409`
  * Other Info: ``
* URL: http://host.docker.internal:8090/api/auth/registro/
  * Node Name: `http://host.docker.internal:8090/api/auth/registro/ ()({usuario,password,nombre})`
  * Method: `POST`
  * Parameter: ``
  * Attack: ``
  * Evidence: `404`
  * Other Info: ``
* URL: http://host.docker.internal:8090/api/productos
  * Node Name: `http://host.docker.internal:8090/api/productos ()({nombre,descripcion,precio,idCategoria,stockReferencial,disponible})`
  * Method: `POST`
  * Parameter: ``
  * Attack: ``
  * Evidence: `400`
  * Other Info: ``
* URL: http://host.docker.internal:8090/api/productos/
  * Node Name: `http://host.docker.internal:8090/api/productos/ ()({nombre,descripcion,precio,idCategoria,stockReferencial,disponible})`
  * Method: `POST`
  * Parameter: ``
  * Attack: ``
  * Evidence: `404`
  * Other Info: ``
* URL: http://host.docker.internal:8090/api/productos/categorias
  * Node Name: `http://host.docker.internal:8090/api/productos/categorias ()({nombre,descripcion})`
  * Method: `POST`
  * Parameter: ``
  * Attack: ``
  * Evidence: `400`
  * Other Info: ``
* URL: http://host.docker.internal:8090/api/productos/categorias/
  * Node Name: `http://host.docker.internal:8090/api/productos/categorias/ ()({nombre,descripcion})`
  * Method: `POST`
  * Parameter: ``
  * Attack: ``
  * Evidence: `404`
  * Other Info: ``
* URL: http://host.docker.internal:8090/computeMetadata/v1/
  * Node Name: `http://host.docker.internal:8090/computeMetadata/v1/ ()({usuario,password})`
  * Method: `POST`
  * Parameter: ``
  * Attack: ``
  * Evidence: `404`
  * Other Info: ``
* URL: http://host.docker.internal:8090/latest/meta-data/
  * Node Name: `http://host.docker.internal:8090/latest/meta-data/ ()({usuario,password})`
  * Method: `POST`
  * Parameter: ``
  * Attack: ``
  * Evidence: `404`
  * Other Info: ``
* URL: http://host.docker.internal:8090/metadata/instance
  * Node Name: `http://host.docker.internal:8090/metadata/instance ()({usuario,password})`
  * Method: `POST`
  * Parameter: ``
  * Attack: ``
  * Evidence: `404`
  * Other Info: ``
* URL: http://host.docker.internal:8090/metadata/v1
  * Node Name: `http://host.docker.internal:8090/metadata/v1 ()({usuario,password})`
  * Method: `POST`
  * Parameter: ``
  * Attack: ``
  * Evidence: `404`
  * Other Info: ``
* URL: http://host.docker.internal:8090/opc/v1/instance/
  * Node Name: `http://host.docker.internal:8090/opc/v1/instance/ ()({usuario,password})`
  * Method: `POST`
  * Parameter: ``
  * Attack: ``
  * Evidence: `404`
  * Other Info: ``
* URL: http://host.docker.internal:8090/opc/v2/instance/
  * Node Name: `http://host.docker.internal:8090/opc/v2/instance/ ()({usuario,password})`
  * Method: `POST`
  * Parameter: ``
  * Attack: ``
  * Evidence: `404`
  * Other Info: ``
* URL: http://host.docker.internal:8090/openstack/latest/meta_data.json
  * Node Name: `http://host.docker.internal:8090/openstack/latest/meta_data.json ()({usuario,password})`
  * Method: `POST`
  * Parameter: ``
  * Attack: ``
  * Evidence: `404`
  * Other Info: ``
* URL: http://host.docker.internal:8090/api/productos/1
  * Node Name: `http://host.docker.internal:8090/api/productos/1 ()({nombre,descripcion,precio,idCategoria,stockReferencial,disponible})`
  * Method: `PUT`
  * Parameter: ``
  * Attack: ``
  * Evidence: `400`
  * Other Info: ``
* URL: http://host.docker.internal:8090/api/productos/1/
  * Node Name: `http://host.docker.internal:8090/api/productos/1/ ()({nombre,descripcion,precio,idCategoria,stockReferencial,disponible})`
  * Method: `PUT`
  * Parameter: ``
  * Attack: ``
  * Evidence: `404`
  * Other Info: ``


Instances: 48

### Solution



### Reference



#### CWE Id: [ 388 ](https://cwe.mitre.org/data/definitions/388.html)


#### WASC Id: 20

#### Source ID: 4

### [ Non-Storable Content ](https://www.zaproxy.org/docs/alerts/10049/)



##### Informational (Medium)

### Description

The response contents are not storable by caching components such as proxy servers. If the response does not contain sensitive, personal or user-specific information, it may benefit from being stored and cached, to improve performance.

* URL: http://host.docker.internal:8090/api/productos%3Fcategoria=3&q=monitor&soloDisponibles=false
  * Node Name: `http://host.docker.internal:8090/api/productos (categoria,q,soloDisponibles)`
  * Method: `GET`
  * Parameter: ``
  * Attack: ``
  * Evidence: `no-store`
  * Other Info: ``
* URL: http://host.docker.internal:8090/api/productos/categorias
  * Node Name: `http://host.docker.internal:8090/api/productos/categorias`
  * Method: `GET`
  * Parameter: ``
  * Attack: ``
  * Evidence: `no-store`
  * Other Info: ``
* URL: http://host.docker.internal:8090/api/auth/login
  * Node Name: `http://host.docker.internal:8090/api/auth/login ()({usuario,password})`
  * Method: `POST`
  * Parameter: ``
  * Attack: ``
  * Evidence: `no-store`
  * Other Info: ``
* URL: http://host.docker.internal:8090/api/auth/registro
  * Node Name: `http://host.docker.internal:8090/api/auth/registro ()({usuario,password,nombre})`
  * Method: `POST`
  * Parameter: ``
  * Attack: ``
  * Evidence: `no-store`
  * Other Info: ``
* URL: http://host.docker.internal:8090/api/productos
  * Node Name: `http://host.docker.internal:8090/api/productos ()({nombre,descripcion,precio,idCategoria,stockReferencial,disponible})`
  * Method: `POST`
  * Parameter: ``
  * Attack: ``
  * Evidence: `no-store`
  * Other Info: ``

Instances: Systemic


### Solution

The content may be marked as storable by ensuring that the following conditions are satisfied:
The request method must be understood by the cache and defined as being cacheable ("GET", "HEAD", and "POST" are currently defined as cacheable)
The response status code must be understood by the cache (one of the 1XX, 2XX, 3XX, 4XX, or 5XX response classes are generally understood)
The "no-store" cache directive must not appear in the request or response header fields
For caching by "shared" caches such as "proxy" caches, the "private" response directive must not appear in the response
For caching by "shared" caches such as "proxy" caches, the "Authorization" header field must not appear in the request, unless the response explicitly allows it (using one of the "must-revalidate", "public", or "s-maxage" Cache-Control response directives)
In addition to the conditions above, at least one of the following conditions must also be satisfied by the response:
It must contain an "Expires" header field
It must contain a "max-age" response directive
For "shared" caches such as "proxy" caches, it must contain a "s-maxage" response directive
It must contain a "Cache Control Extension" that allows it to be cached
It must have a status code that is defined as cacheable by default (200, 203, 204, 206, 300, 301, 404, 405, 410, 414, 501).

### Reference


* [ https://datatracker.ietf.org/doc/html/rfc7234 ](https://datatracker.ietf.org/doc/html/rfc7234)
* [ https://datatracker.ietf.org/doc/html/rfc7231 ](https://datatracker.ietf.org/doc/html/rfc7231)
* [ https://www.w3.org/Protocols/rfc2616/rfc2616-sec13.html ](https://www.w3.org/Protocols/rfc2616/rfc2616-sec13.html)


#### CWE Id: [ 524 ](https://cwe.mitre.org/data/definitions/524.html)


#### WASC Id: 13

#### Source ID: 3


