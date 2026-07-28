(module
  (memory (export "memory") 1)
  (global $next (mut i32) (i32.const 2048))

  (func (export "cabi_realloc") (param $oldPtr i32) (param $oldSize i32) (param $align i32) (param $newSize i32) (result i32)
    (local $p i32)
    global.get $next
    local.get $align i32.add i32.const 1 i32.sub
    local.get $align i32.const 1 i32.sub i32.const -1 i32.xor
    i32.and
    local.tee $p
    local.get $newSize i32.add
    global.set $next
    local.get $p)

  (func (export "abi-version") (result i32) i32.const 1)
  (func (export "init"))

  ;; hook 0: cancel WasmHookTarget.compute() and return 42
  ;; hook 1: cancel WasmHookTarget.add(a, b) and return args count * 100
  (func (export "on-hook") (param $hook i32) (param $argsPtr i32) (param $argsLen i32) (result i32)
    i32.const 1024 i32.const 0 i32.const 24 memory.fill
    i32.const 1024 i32.const 2 i32.store8        ;; return-value
    i32.const 1032 i32.const 5 i32.store8        ;; value.kind = i32
    local.get $hook
    i32.eqz
    if
      i32.const 1040 i64.const 42 i64.store
    else
      i32.const 1040 local.get $argsLen i64.extend_i32_s i64.const 100 i64.mul i64.store
    end
    i32.const 1024)

  (func (export "cabi_post_on-hook") (param i32))
)
