import { useEffect, useState } from 'react'

/** The value, but only after it has stopped changing for `delayMs`. Keeps typing from firing a request per key. */
export function useDebounced<T>(value: T, delayMs = 250): T {
  const [settled, setSettled] = useState(value)
  useEffect(() => {
    const timer = setTimeout(() => setSettled(value), delayMs)
    return () => clearTimeout(timer)
  }, [value, delayMs])
  return settled
}
