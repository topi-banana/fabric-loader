;; Hand written guest implementing the fabric:mod world's core ABI shape.
;; Exists to exercise the host's Canonical ABI without a language toolchain.
(module
  (memory (export "memory") 1)

  ;; bump allocator standing in for cabi_realloc
  (global $next (mut i32) (i32.const 2048))

  (func (export "cabi_realloc") (param $oldPtr i32) (param $oldSize i32) (param $align i32) (param $newSize i32) (result i32)
    (local $p i32)
    ;; p = (next + align - 1) & ~(align - 1)
    global.get $next
    local.get $align
    i32.add
    i32.const 1
    i32.sub
    local.get $align
    i32.const 1
    i32.sub
    i32.const -1
    i32.xor
    i32.and
    local.tee $p
    local.get $newSize
    i32.add
    global.set $next
    local.get $p)

  (func (export "abi-version") (result i32) i32.const 1)

  (func (export "init"))

  ;; on-hook(hook: u32, argsPtr: i32, argsLen: i32) -> pointer to a 24 byte outcome
  ;;   hook 0 -> proceed
  ;;   hook 1 -> cancel
  ;;   hook 2 -> return-value(i32 = number of args)
  ;;   hook 3 -> replace(f64 = 2.5)
  ;;   hook 4 -> return-value(i32 = bits of args[0]) so the host can prove lowering works
  ;;   hook 5 -> failed("guest said no")
  (func (export "on-hook") (param $hook i32) (param $argsPtr i32) (param $argsLen i32) (result i32)
    ;; the outcome lives at a fixed address; zero the payload first
    i32.const 1024 i32.const 0 i32.const 24 memory.fill

    local.get $hook
    i32.const 1
    i32.eq
    if
      i32.const 1024 i32.const 1 i32.store8      ;; cancel
      i32.const 1024 return
    end

    local.get $hook
    i32.const 2
    i32.eq
    if
      i32.const 1024 i32.const 2 i32.store8      ;; return-value
      i32.const 1032 i32.const 5 i32.store8      ;; value.kind = i32
      i32.const 1040 local.get $argsLen i64.extend_i32_s i64.store  ;; value.bits
      i32.const 1024 return
    end

    local.get $hook
    i32.const 3
    i32.eq
    if
      i32.const 1024 i32.const 3 i32.store8      ;; replace
      i32.const 1032 i32.const 8 i32.store8      ;; value.kind = f64
      i32.const 1040 f64.const 2.5 i64.reinterpret_f64 i64.store
      i32.const 1024 return
    end

    local.get $hook
    i32.const 4
    i32.eq
    if
      i32.const 1024 i32.const 2 i32.store8      ;; return-value
      i32.const 1032 i32.const 5 i32.store8      ;; value.kind = i32
      ;; echo back args[0].bits, proving the host laid the list out at stride 16
      i32.const 1040 local.get $argsPtr i32.const 8 i32.add i64.load i64.store
      i32.const 1024 return
    end

    local.get $hook
    i32.const 5
    i32.eq
    if
      i32.const 1024 i32.const 7 i32.store8      ;; failed
      ;; the message lives in the data segment below; the payload carries pointer and length
      i32.const 1032 i32.const 1500 i32.store             ;; string pointer
      i32.const 1036 i32.const 13 i32.store               ;; string length
      i32.const 1024 return
    end

    i32.const 1024)                                       ;; proceed

  ;; "guest said no" as a data segment the failed case points at
  (data (i32.const 1500) "guest said no")
)
