import React from 'react';
// Refresh records without resetting draft forms or memory-only credentials.
export default function usePageRefresh(load:()=>unknown){
 const latest=React.useRef(load);latest.current=load;
 React.useEffect(()=>{const refresh=()=>{void latest.current()};window.addEventListener('zt-refresh',refresh);return()=>window.removeEventListener('zt-refresh',refresh)},[]);
}
