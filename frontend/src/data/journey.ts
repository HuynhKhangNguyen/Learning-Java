import { pair, type Pair } from '../i18n/types.ts';

// Navigation outline stays separate from the lesson catalog until T11; optional lesson IDs connect available content.
export type MapTopic = [title: Pair, goal: Pair, lessonId?: string];
export type MapStage = {
  id: string;
  title: Pair;
  label: Pair;
  tone: 'mint' | 'blue' | 'amber';
  icon: number;
  topics: MapTopic[];
};

const docker: MapStage = {
  id: 'docker',
  title: pair('Docker & deployment', 'Docker & deployment'),
  label: pair('Ship the application', 'Đưa ứng dụng vào vận hành'),
  tone: 'blue',
  icon: 10,
  topics: [
    [pair('Image & container', 'Image & container'), pair('Tell the packaged application apart from the process running from that package.', 'Phân biệt image chứa ứng dụng với container và process được tạo từ image đó.')],
    [pair('Dockerfile', 'Dockerfile'), pair('Package Java with a multi-stage build and configure its runtime.', 'Đóng gói ứng dụng Java bằng multi-stage build và cấu hình môi trường chạy.')],
    [pair('Network & volume', 'Network & volume'), pair('Connect a database and preserve data when a container is recreated.', 'Kết nối database và giữ dữ liệu khi tạo lại container.')],
    [pair('Docker Compose', 'Docker Compose'), pair('Start an API and PostgreSQL together with repeatable configuration.', 'Khởi chạy đồng thời API và PostgreSQL bằng một cấu hình có thể tái sử dụng.')],
    [pair('Config & secrets', 'Config & secrets'), pair('Separate environment configuration and keep passwords out of images.', 'Tách cấu hình theo từng môi trường và không đưa password vào image.')],
    [pair('Health check & deploy', 'Health check & deploy'), pair('Check application health, practise deployment, and roll back safely.', 'Kiểm tra tình trạng ứng dụng, thực hành triển khai và rollback an toàn.')],
  ],
};

export const javaStages: MapStage[] = [
  {
    id: 'foundations',
    title: pair('Backend foundations', 'Nền tảng Backend'),
    label: pair('Start here', 'Bắt đầu từ đây'),
    tone: 'mint',
    icon: 0,
    topics: [
      [pair('Internet & DNS', 'Internet & DNS'), pair('Follow the journey from a domain name to a server address.', 'Theo dõi cách một domain name được phân giải thành địa chỉ IP của server.')],
      [pair('Client & server', 'Client & server'), pair('Explain how a browser sends a request and the backend returns a response.', 'Giải thích cách trình duyệt gửi request và backend trả về response.')],
      [pair('Terminal & Git', 'Terminal & Git'), pair('Run commands, create commits, and work on a separate branch.', 'Chạy lệnh trong terminal, tạo commit và làm việc trên branch riêng.')],
      [pair('JDK & IDE', 'JDK & IDE'), pair('Compile and run Java, and tell the JDK apart from the JVM.', 'Biên dịch, chạy chương trình Java và phân biệt JDK với JVM.')],
    ],
  },
  {
    id: 'java',
    title: pair('Think in Java', 'Tư duy bằng Java'),
    label: pair('Language', 'Ngôn ngữ'),
    tone: 'amber',
    icon: 1,
    topics: [
      [pair('Data types & variables', 'Data types & variables'), pair('Choose data types and understand values, references, and null.', 'Chọn data type phù hợp và hiểu value, reference cùng null.')],
      [pair('Conditionals & loops', 'Conditionals & loops'), pair('Express rules with conditionals and loops.', 'Dùng câu lệnh rẽ nhánh và vòng lặp để biểu diễn quy tắc.')],
      [pair('Method & scope', 'Method & scope'), pair('Break a program into functions with clear inputs and outputs.', 'Chia chương trình thành các method có đầu vào, đầu ra và scope rõ ràng.')],
      [pair('String & array', 'String & array'), pair('Process strings and traverse fixed-size data collections.', 'Xử lý String và duyệt tập dữ liệu có kích thước cố định bằng array.')],
    ],
  },
  {
    id: 'oop',
    title: pair('Model and process data', 'Mô hình hóa & xử lý dữ liệu'),
    label: pair('Core Java', 'Java cốt lõi'),
    tone: 'amber',
    icon: 2,
    topics: [
      [pair('Class & object', 'Class & object'), pair('Model products and orders through state and behaviour.', 'Mô hình hóa sản phẩm và đơn hàng bằng state cùng behavior của object.')],
      [pair('Interface & composition', 'Interface & composition'), pair('Separate contracts from implementations and compose objects.', 'Tách contract khỏi implementation và phối hợp nhiều object bằng composition.')],
      [pair('Collections & generics', 'Collections & generics'), pair('Choose List, Set, or Map and use generic types safely.', 'Chọn đúng List, Set hoặc Map và sử dụng generic type an toàn.')],
      [pair('Exception', 'Exception'), pair('Handle errors or propagate them to the appropriate caller.', 'Xử lý lỗi tại chỗ hoặc chuyển Exception lên caller phù hợp.')],
      [pair('Stream & lambda', 'Stream & lambda'), pair('Filter, transform, and aggregate data through a pipeline.', 'Dùng Stream và lambda để lọc, biến đổi và tổng hợp dữ liệu theo pipeline.')],
      [pair('JVM & memory', 'JVM & memory'), pair('Tell stack and heap apart and explain the garbage collector.', 'Phân biệt stack, heap và giải thích vai trò của garbage collector.')],
    ],
  },
  {
    id: 'quality',
    title: pair('Write trustworthy code', 'Viết code đáng tin cậy'),
    label: pair('Tools & quality', 'Tools & chất lượng'),
    tone: 'mint',
    icon: 3,
    topics: [
      [pair('Maven & Gradle', 'Maven & Gradle'), pair('Understand dependencies, the build lifecycle, and JAR packaging.', 'Hiểu dependency, vòng đời build và cách đóng gói JAR.')],
      [pair('Debug & logging', 'Debug & logging'), pair('Set breakpoints, read stack traces, and find root causes.', 'Đặt breakpoint, đọc stack trace và tìm nguyên nhân gốc của lỗi.')],
      [pair('JUnit & Mockito', 'JUnit & Mockito'), pair('Test behaviour and isolate dependencies when needed.', 'Kiểm thử hành vi bằng JUnit và cô lập dependency bằng Mockito khi cần.')],
      [pair('Clean code & refactoring', 'Clean code & refactoring'), pair('Separate responsibilities and improve code without changing behaviour.', 'Tách rõ trách nhiệm và refactor code mà không làm thay đổi hành vi.')],
    ],
  },
  {
    id: 'http',
    title: pair('The language of the web', 'Ngôn ngữ của Web'),
    label: pair('Communication', 'Giao tiếp'),
    tone: 'blue',
    icon: 4,
    topics: [
      [pair('HTTP request / response', 'HTTP request / response'), pair('Read methods, URLs, headers, bodies, and status codes.', 'Đọc HTTP method, URL, header, body và status code.')],
      [pair('REST & JSON', 'REST & JSON'), pair('Design resources and easy-to-use API contracts.', 'Thiết kế resource và API contract rõ ràng, dễ sử dụng.')],
      [pair('Validation & error handling', 'Validation & xử lý lỗi'), pair('Return consistent errors and distinguish client errors from server errors.', 'Trả lỗi theo cấu trúc nhất quán và phân biệt lỗi do request với lỗi nội bộ của server.'), 'api-debug-500'],
      [pair('Pagination & idempotency', 'Pagination & idempotency'), pair('Design pagination and handle repeated requests safely.', 'Thiết kế pagination và xử lý request gửi lặp an toàn.')],
    ],
  },
  {
    id: 'spring',
    title: pair('Build APIs with Spring Boot', 'Xây API với Spring Boot'),
    label: pair('Framework', 'Framework'),
    tone: 'mint',
    icon: 5,
    topics: [
      [pair('IoC & dependency injection', 'IoC & dependency injection'), pair('Explain how Spring creates and wires beans.', 'Giải thích cách Spring tạo bean và inject các dependency.')],
      [pair('Controller → service', 'Controller → service'), pair('Follow a request through the controller and business logic.', 'Theo dõi request từ controller đến phần xử lý nghiệp vụ trong service.')],
      [pair('DTO & validation', 'DTO & validation'), pair('Separate API data from persistence models and validate input.', 'Dùng DTO để tách dữ liệu API khỏi persistence model, rồi dùng validation để kiểm tra dữ liệu đầu vào.')],
      [pair('Configuration & profiles', 'Configuration & profiles'), pair('Switch configuration across local, test, and production environments.', 'Chuyển cấu hình giữa các môi trường local, test và production bằng profile.')],
      [pair('Integration testing', 'Integration testing'), pair('Verify that multiple components work together through a request.', 'Dùng integration test để kiểm tra nhiều component phối hợp trong một request.')],
    ],
  },
  {
    id: 'sql',
    title: pair('Durable data', 'Dữ liệu bền vững'),
    label: pair('Database', 'Database'),
    tone: 'blue',
    icon: 6,
    topics: [
      [pair('SQL & PostgreSQL', 'SQL & PostgreSQL'), pair('Read, insert, update, and delete data with SQL.', 'Dùng SQL để đọc, thêm, cập nhật và xóa dữ liệu trong PostgreSQL.')],
      [pair('Schema & relationships', 'Schema & relationships'), pair('Design tables, primary keys, foreign keys, and constraints.', 'Thiết kế table, primary key, foreign key, relationship và constraint.')],
      [pair('JOIN & index', 'JOIN & index'), pair('Combine data and see how indexes affect queries.', 'Kết hợp dữ liệu bằng JOIN và quan sát index ảnh hưởng đến query ra sao.')],
      [pair('Transaction & isolation', 'Transaction & isolation'), pair('Keep data consistent when multiple operations happen concurrently.', 'Dùng transaction và isolation để giữ dữ liệu nhất quán khi nhiều thao tác chạy đồng thời.')],
    ],
  },
  {
    id: 'persistence',
    title: pair('Connect Java to a database', 'Kết nối Java với database'),
    label: pair('Persistence', 'Persistence'),
    tone: 'blue',
    icon: 7,
    topics: [
      [pair('JDBC → JPA', 'JDBC → JPA'), pair('Understand ORM and the SQL underneath it.', 'Hiểu ORM và SQL chạy bên dưới.')],
      [pair('Entity & repository', 'Entity & repository'), pair('Map data and build a database access layer.', 'Ánh xạ dữ liệu bằng entity và xây dựng lớp truy cập database bằng repository.')],
      [pair('Lazy loading & N+1', 'Lazy loading & N+1'), pair('Recognise excessive queries and choose a fetching strategy.', 'Nhận diện query dư thừa do N+1 và chọn fetching strategy phù hợp.')],
      [pair('Migration & Testcontainers', 'Migration & Testcontainers'), pair('Manage schema changes and test against a real database.', 'Quản lý thay đổi schema bằng migration và kiểm thử với database thật qua Testcontainers.')],
    ],
  },
  {
    id: 'security',
    title: pair('Protect the application', 'Bảo vệ ứng dụng'),
    label: pair('Security', 'Security'),
    tone: 'amber',
    icon: 8,
    topics: [
      [pair('Authentication & authorization', 'Authentication & authorization'), pair('Tell identity verification apart from permission checks.', 'Phân biệt authentication (xác thực danh tính) với authorization (kiểm tra quyền).')],
      [pair('Session & token', 'Session & token'), pair('Compare login sessions with tokens and understand their lifecycles.', 'So sánh session đăng nhập với token và hiểu lifecycle của từng loại.')],
      [pair('Spring Security', 'Spring Security'), pair('Build a filter chain and protect endpoints by permission.', 'Xây dựng filter chain và bảo vệ endpoint theo quyền của người dùng.')],
      [pair('Password, CORS & CSRF', 'Password, CORS & CSRF'), pair('Understand password hashing, origin policies, and request forgery.', 'Hiểu password hashing, chính sách origin và cách CSRF lợi dụng request giả mạo.')],
    ],
  },
  {
    id: 'scale',
    title: pair('Backend under load', 'Backend khi tải tăng'),
    label: pair('Advanced', 'Nâng cao'),
    tone: 'amber',
    icon: 9,
    topics: [
      [pair('Concurrency', 'Concurrency'), pair('Recognise race conditions and manage concurrent work.', 'Nhận diện race condition và quản lý công việc đồng thời.')],
      [pair('Redis & cache', 'Redis & cache'), pair('Choose what to cache and explain invalidation.', 'Chọn dữ liệu để cache và giải thích cache invalidation.')],
      [pair('Queue & messaging', 'Queue & messaging'), pair('Move work to the background, retry failures, and handle duplicate messages.', 'Đưa công việc sang xử lý background, retry khi lỗi và chống xử lý message trùng.')],
      [pair('Timeout & resilience', 'Timeout & resilience'), pair('Bound wait times and handle dependency failures.', 'Đặt timeout rõ ràng và xử lý an toàn khi dependency gặp lỗi.')],
    ],
  },
  docker,
  {
    id: 'capstone',
    title: pair('Complete backend', 'Backend hoàn chỉnh'),
    label: pair('Capstone project', 'Dự án tổng hợp'),
    tone: 'mint',
    icon: 11,
    topics: [
      [pair('System design', 'System design'), pair('Split components by requirements and explain the trade-offs.', 'Chia hệ thống thành các component theo requirement và giải thích từng trade-off.')],
      [pair('CI/CD', 'CI/CD'), pair('Automate testing, builds, and deployments.', 'Tự động hóa kiểm thử, build và deploy bằng pipeline CI/CD.')],
      [pair('Metrics, logs & traces', 'Metrics, logs & traces'), pair('Trace a failure from its symptom to the component that caused it.', 'Dùng metrics, logs và traces để lần từ triệu chứng đến component gây lỗi.')],
      [pair('Order management project', 'Dự án quản lý đơn hàng'), pair('Combine APIs, databases, security, and Docker into a working product.', 'Kết hợp API, database, Security và Docker thành một sản phẩm chạy được.')],
    ],
  },
];

export const devopsStages: MapStage[] = [
  {
    id: 'linux',
    title: pair('Linux & networking', 'Linux & networking'),
    label: pair('Runtime environment', 'Môi trường chạy'),
    tone: 'mint',
    icon: 9,
    topics: [
      [pair('Process & filesystem', 'Process & filesystem'), pair('Inspect processes, permissions, and disk usage.', 'Kiểm tra process, permission và mức sử dụng ổ đĩa trong filesystem.')],
      [pair('TCP, DNS & TLS', 'TCP, DNS & TLS'), pair('Diagnose connections and understand how TLS protects data in transit.', 'Chẩn đoán kết nối và hiểu cách TLS bảo vệ dữ liệu trên đường truyền.')],
      [pair('Shell & SSH', 'Shell & SSH'), pair('Automate basic commands and connect to a server.', 'Tự động hóa các lệnh shell cơ bản và kết nối đến server qua SSH.')],
      [pair('Reverse proxy', 'Reverse proxy'), pair('Route requests to the application through a proxy and configure HTTPS.', 'Định tuyến request qua reverse proxy đến ứng dụng và cấu hình HTTPS.')],
    ],
  },
  docker,
  {
    id: 'pipeline',
    title: pair('From commit to deployment', 'Từ commit đến triển khai'),
    label: pair('CI/CD', 'CI/CD'),
    tone: 'blue',
    icon: 11,
    topics: [
      [pair('Pipeline & artifacts', 'Pipeline & artifacts'), pair('Run tests and produce versioned artifacts.', 'Chạy kiểm thử và tạo artifact có version rõ ràng trong pipeline.')],
      [pair('Registry', 'Registry'), pair('Push and pull images with traceable tags.', 'Push và pull image bằng các tag có thể truy vết.')],
      [pair('Environment & rollback', 'Environment & rollback'), pair('Deploy across environments and return to a previous version.', 'Triển khai qua nhiều môi trường và rollback về version trước khi cần.')],
      [pair('Secrets in CI', 'Secrets trong CI'), pair('Grant least privilege and manage secrets in the pipeline.', 'Áp dụng least privilege và quản lý secret an toàn trong pipeline.')],
    ],
  },
  {
    id: 'cloud',
    title: pair('Repeatable infrastructure', 'Hạ tầng có thể tái tạo'),
    label: pair('Cloud & IaC', 'Cloud & IaC'),
    tone: 'amber',
    icon: 4,
    topics: [
      [pair('Compute & network', 'Compute & network'), pair('Choose compute, subnets, and access rules.', 'Chọn compute, subnet và access rule phù hợp với hệ thống.')],
      [pair('Storage & backup', 'Storage & backup'), pair('Plan storage and verify data restoration.', 'Lập kế hoạch storage, backup và kiểm tra khả năng restore dữ liệu.')],
      [pair('Infrastructure as code', 'Infrastructure as code'), pair('Describe infrastructure and manage its changes as code.', 'Mô tả hạ tầng bằng code và quản lý thay đổi qua version control.')],
      [pair('Cost & permissions', 'Cost & permissions'), pair('Track costs and restrict access permissions.', 'Theo dõi chi phí và giới hạn quyền truy cập theo nhu cầu thực tế.')],
    ],
  },
  {
    id: 'orchestration',
    title: pair('Container orchestration', 'Container orchestration'),
    label: pair('Kubernetes · advanced', 'Kubernetes · chuyên sâu'),
    tone: 'blue',
    icon: 10,
    topics: [
      [pair('Pod & deployment', 'Pod & deployment'), pair('Describe workloads and roll out a new version.', 'Mô tả workload và rollout một version mới bằng Deployment.')],
      [pair('Service & ingress', 'Service & ingress'), pair('Route traffic to a workload.', 'Định tuyến traffic đến workload qua Service và Ingress.')],
      [pair('Config & storage', 'Config & storage'), pair('Provide configuration and persistent data to a workload.', 'Cung cấp cấu hình và persistent data cho workload.')],
      [pair('Scaling & probes', 'Scaling & probes'), pair('Scale replicas and check readiness and liveness.', 'Tăng hoặc giảm số replica, đồng thời kiểm tra readiness và liveness.')],
    ],
  },
  {
    id: 'reliability',
    title: pair('Reliable operations', 'Vận hành đáng tin cậy'),
    label: pair('Observability & SRE', 'Observability & SRE'),
    tone: 'mint',
    icon: 3,
    topics: [
      [pair('Metrics & alerts', 'Metrics & alerts'), pair('Monitor important signals and create actionable alerts.', 'Theo dõi các tín hiệu quan trọng và tạo alert đủ rõ để xử lý.')],
      [pair('Logs & tracing', 'Logs & tracing'), pair('Follow a request across multiple components.', 'Theo dõi request qua nhiều component.')],
      [pair('SLO & incident', 'SLO & incident'), pair('Set reliability objectives and practise incident response.', 'Đặt SLO và thực hành quy trình incident response.')],
      [pair('Recovery drill', 'Recovery drill'), pair('Rehearse restoration to verify that backups work.', 'Diễn tập restore để xác minh backup thực sự dùng được.')],
    ],
  },
];
