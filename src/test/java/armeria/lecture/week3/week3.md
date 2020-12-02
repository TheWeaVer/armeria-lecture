
HTTP 1.1
1.1은 커넥션을 재사용한다. 만약 아직 반환되지 않았다면, 새로 만들어야 한다.
1.0은 아니다. 사용하려면 keep-alive header를 보내야 한다.

1.1 pipelining
응답을 받지 않아도, 계속해서 요청을 보낸다.
armeria는 connection pool을 별도로 관리하고 재사용하고 있다 -> pooledChannel
useHttpPipelining -> 안쓰면 req/res를 받아서 재사용하도록 한다.
어떻게? 비효율적이다. 가변적인 패킷길이를 보통 갖고있는데, CRLF를 찾을때까지 읽는다.
method, auth header등을 CRLF 기반으로 byte단위로 찾는다.
body는 content-length를 같이 보내줘서 body의 byte를 알 수 있다.
섞이면 어떤 요청의 헤더인지 바디인지 몰라서, 순차적으로 보낸다.

Streaming response
Transfer-Encoding: chunkedCRLF 로 사용가능


HTTP/2
Frame format이 존재한다. Length(24)|Type(8)|Flags(8)|R|Stream Identifier(31)|Frame Payload(0..)|
DATA frames: type = 0x0
HEADER frame: type = 0x1
Stream id는 1, 3, 5 ...이다. 왜냐면, server 측에서 push는 짝수이기 때무네
즉, 순서대로 보낼필요가 없다.

Pseudo-Header Fields
:method :scheme  :authority :path :status
- END_STREAM : STREAM이 끝나지 않았다.
+ END_HEADERS: 여기서 header는 끝이나 - 라면 HEADERS가 남아있다는 의미

Whole flow
HttpResponse res = client.execute(req);
HttpResponse <- HttpResponseDecoder <- recv buffer <- ...packets... <- send buffer <- HttpResponseSubcriber <- HttpService
