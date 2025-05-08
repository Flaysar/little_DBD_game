% Подключаем необходимые библиотеки:
% - library(socket) для сетевого взаимодействия
% - library(http/json) для работы с JSON
:- use_module(library(socket)).
:- use_module(library(http/json)).

% Основная функция подключения к серверу
connect_to_server :-
    % Устанавливаем TCP-соединение с сервером на localhost:12345
    % Stream - поток для обмена данными
    tcp_connect(localhost:12345, Stream, [type(text)]),
    
    % Устанавливаем кодировку UTF-8 для потока
    set_stream(Stream, encoding(utf8)),
    
    % Выводим инструкции для пользователя
    format('Connected to server. Use WASD to move, Q to quit~n~n', []),
    
    % Запускаем основной цикл клиента
    client_loop(Stream).

% Функция отображения игровой карты из JSON-строки
display_map(JsonString) :-
    % Создаем временный поток из строки JSON
    open_string(JsonString, Stream),
    
    % Парсим JSON из потока в словарь Prolog
    json_read_dict(Stream, JsonTerm, [value_string_as(atom)]),
    
    % Закрываем временный поток
    close(Stream),
    
    % Проверяем корректность данных и извлекаем карту
    (   is_dict(JsonTerm), get_dict(map, JsonTerm, Map)
    ->  % Если данные валидны - выводим каждую строку карты
        maplist(format('~s~n'), Map)
    ;   % Если данные невалидны - выводим сообщение об ошибке
        format('Invalid data format: ~w~n', [JsonString])
    ).

% Основной цикл клиента
client_loop(Stream) :-
    % Получаем один символ от пользователя
    get_single_char(Char),
    
    % Обрабатываем ввод пользователя
    (   char_code('q', Char)  % Если нажата 'q'
    ->  % Завершаем работу
        format('Quitting...~n', []),
        close(Stream)
    ;   (   memberchk(Char, [0'w, 0's, 0'a, 0'd])  % Если WASD
        ->  % Отправляем команду серверу
            format(Stream, '~c~n', [Char]),
            flush_output(Stream),
            
            % Получаем ответ от сервера
            (   read_line_to_string(Stream, Response)
            ->  % Очищаем экран (выводим много пустых строк)
                format('~n~n~n~n~n~n~n~n~n~n~n', []),
                
                % Отображаем обновленную карту
                display_map(Response)
            ;   % Если соединение разорвано
                format('Server disconnected~n', []),
                close(Stream)
            )
        ;   % Игнорируем другие символы
            true
        ),
        
        % Рекурсивно продолжаем цикл
        client_loop(Stream)
    ).

% Точка входа - сразу запускаем подключение к серверу
:- connect_to_server.